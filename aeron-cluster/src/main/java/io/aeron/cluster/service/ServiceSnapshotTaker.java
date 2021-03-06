/*
 * Copyright 2018 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.cluster.service;

import io.aeron.Publication;
import io.aeron.cluster.codecs.*;
import org.agrona.concurrent.AgentInvoker;
import org.agrona.concurrent.IdleStrategy;

class ServiceSnapshotTaker extends SnapshotTaker
{
    private final ClientSessionEncoder clientSessionEncoder = new ClientSessionEncoder();

    ServiceSnapshotTaker(
        final Publication publication, final IdleStrategy idleStrategy, final AgentInvoker aeronClientInvoker)
    {
        super(publication, idleStrategy, aeronClientInvoker);
    }

    public void snapshotSession(final ClientSession session)
    {
        final int responseStreamId = session.responsePublication().streamId();
        final String responseChannel = session.responsePublication().channel();
        final byte[] principleData = session.principleData();
        final int length = MessageHeaderEncoder.ENCODED_LENGTH + ClientSessionEncoder.BLOCK_LENGTH +
            ClientSessionEncoder.responseChannelHeaderLength() + responseChannel.length() +
            ClientSessionEncoder.principleDataHeaderLength() + principleData.length;

        idleStrategy.reset();
        while (true)
        {
            final long result = publication.tryClaim(length, bufferClaim);
            if (result > 0)
            {
                clientSessionEncoder
                    .wrapAndApplyHeader(bufferClaim.buffer(), bufferClaim.offset(), messageHeaderEncoder)
                    .clusterSessionId(session.id())
                    .responseStreamId(responseStreamId)
                    .responseChannel(responseChannel)
                    .putPrincipleData(principleData, 0, principleData.length);

                bufferClaim.commit();
                break;
            }

            checkResultAndIdle(result);
        }
    }
}
