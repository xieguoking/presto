/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.tpch;

import com.facebook.presto.spi.ConnectorHandleResolver;
import com.facebook.presto.spi.NodeManager;
import com.facebook.presto.spi.connector.Connector;
import com.facebook.presto.spi.connector.ConnectorContext;
import com.facebook.presto.spi.connector.ConnectorFactory;
import com.facebook.presto.spi.connector.ConnectorMetadata;
import com.facebook.presto.spi.connector.ConnectorNodePartitioningProvider;
import com.facebook.presto.spi.connector.ConnectorRecordSetProvider;
import com.facebook.presto.spi.connector.ConnectorSplitManager;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.transaction.IsolationLevel;

import java.util.Map;

import static com.google.common.base.MoreObjects.firstNonNull;

public class TpchConnectorFactory
        implements ConnectorFactory
{
    public static final boolean DEFAULT_PREDICATE_PUSHDOWN_ENABLED = false;

    private final int defaultSplitsPerNode;
    private final boolean defaultPredicatePushdownEnabled;

    public TpchConnectorFactory()
    {
        this(Runtime.getRuntime().availableProcessors(), DEFAULT_PREDICATE_PUSHDOWN_ENABLED);
    }

    public TpchConnectorFactory(int defaultSplitsPerNode)
    {
        this(defaultSplitsPerNode, DEFAULT_PREDICATE_PUSHDOWN_ENABLED);
    }

    public TpchConnectorFactory(int defaultSplitsPerNode, boolean defaultPredicatePushdownEnabled)
    {
        this.defaultSplitsPerNode = defaultSplitsPerNode;
        this.defaultPredicatePushdownEnabled = defaultPredicatePushdownEnabled;
    }

    @Override
    public String getName()
    {
        return "tpch";
    }

    @Override
    public ConnectorHandleResolver getHandleResolver()
    {
        return new TpchHandleResolver();
    }

    @Override
    public Connector create(String connectorId, Map<String, String> properties, ConnectorContext context)
    {
        int splitsPerNode = getSplitsPerNode(properties);
        boolean predicatePushdownEnabled = isPredicatePushdownEnabled(properties);
        ColumnNaming columnNaming = ColumnNaming.valueOf(properties.getOrDefault("tpch.column-naming", ColumnNaming.SIMPLIFIED.name()).toUpperCase());
        NodeManager nodeManager = context.getNodeManager();

        return new Connector()
        {
            @Override
            public ConnectorTransactionHandle beginTransaction(IsolationLevel isolationLevel, boolean readOnly)
            {
                return TpchTransactionHandle.INSTANCE;
            }

            @Override
            public ConnectorMetadata getMetadata(ConnectorTransactionHandle transaction)
            {
                return new TpchMetadata(connectorId, predicatePushdownEnabled, columnNaming);
            }

            @Override
            public ConnectorSplitManager getSplitManager()
            {
                return new TpchSplitManager(nodeManager, splitsPerNode);
            }

            @Override
            public ConnectorRecordSetProvider getRecordSetProvider()
            {
                return new TpchRecordSetProvider();
            }

            @Override
            public ConnectorNodePartitioningProvider getNodePartitioningProvider()
            {
                return new TpchNodePartitioningProvider(nodeManager, splitsPerNode);
            }
        };
    }

    private int getSplitsPerNode(Map<String, String> properties)
    {
        try {
            return Integer.parseInt(firstNonNull(properties.get("tpch.splits-per-node"), String.valueOf(defaultSplitsPerNode)));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid property tpch.splits-per-node");
        }
    }

    private boolean isPredicatePushdownEnabled(Map<String, String> properties)
    {
        return Boolean.parseBoolean(firstNonNull(properties.get("tpch.predicate-pushdown"), String.valueOf(defaultPredicatePushdownEnabled)));
    }
}
