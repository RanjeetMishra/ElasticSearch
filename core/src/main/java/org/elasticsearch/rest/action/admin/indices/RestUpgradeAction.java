/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.rest.action.admin.indices;

import org.elasticsearch.Version;
import org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusRequest;
import org.elasticsearch.action.admin.indices.upgrade.get.UpgradeStatusResponse;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeRequest;
import org.elasticsearch.action.admin.indices.upgrade.post.UpgradeResponse;
import org.elasticsearch.action.support.IndicesOptions;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.action.RestBuilderListener;

import java.io.IOException;
import java.util.Map;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestRequest.Method.POST;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.RestActions.buildBroadcastShardsHeader;

public class RestUpgradeAction extends BaseRestHandler {
    public RestUpgradeAction(Settings settings, RestController controller) {
        super(settings);
        controller.registerHandler(POST, "/_upgrade", this);
        controller.registerHandler(POST, "/{index}/_upgrade", this);

        controller.registerHandler(GET, "/_upgrade", this);
        controller.registerHandler(GET, "/{index}/_upgrade", this);
    }

    @Override
    public String getName() {
        return "upgrade_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (request.method().equals(RestRequest.Method.GET)) {
            return handleGet(request, client);
        } else if (request.method().equals(RestRequest.Method.POST)) {
            return handlePost(request, client);
        } else {
            throw new IllegalArgumentException("illegal method [" + request.method() + "] for request [" + request.path() + "]");
        }
    }

    private RestChannelConsumer handleGet(final RestRequest request, NodeClient client) {
        UpgradeStatusRequest statusRequest = new UpgradeStatusRequest(Strings.splitStringByCommaToArray(request.param("index")));
        statusRequest.indicesOptions(IndicesOptions.fromRequest(request, statusRequest.indicesOptions()));
        return channel -> client.admin().indices().upgradeStatus(statusRequest, new RestBuilderListener<UpgradeStatusResponse>(channel) {
            @Override
            public RestResponse buildResponse(UpgradeStatusResponse response, XContentBuilder builder) throws Exception {
                builder.startObject();
                response.toXContent(builder, request);
                builder.endObject();
                return new BytesRestResponse(OK, builder);
            }
        });
    }

    private RestChannelConsumer handlePost(final RestRequest request, NodeClient client) {
        UpgradeRequest upgradeReq = new UpgradeRequest(Strings.splitStringByCommaToArray(request.param("index")));
        upgradeReq.indicesOptions(IndicesOptions.fromRequest(request, upgradeReq.indicesOptions()));
        upgradeReq.upgradeOnlyAncientSegments(request.paramAsBoolean("only_ancient_segments", false));
        return channel -> client.admin().indices().upgrade(upgradeReq, new RestBuilderListener<UpgradeResponse>(channel) {
            @Override
            public RestResponse buildResponse(UpgradeResponse response, XContentBuilder builder) throws Exception {
                builder.startObject();
                buildBroadcastShardsHeader(builder, request, response);
                builder.startObject("upgraded_indices");
                for (Map.Entry<String, Tuple<Version, String>> entry : response.versions().entrySet()) {
                    builder.startObject(entry.getKey());
                    builder.field("upgrade_version", entry.getValue().v1());
                    builder.field("oldest_lucene_segment_version", entry.getValue().v2());
                    builder.endObject();
                }
                builder.endObject();
                builder.endObject();
                return new BytesRestResponse(OK, builder);
            }
        });
    }

}
