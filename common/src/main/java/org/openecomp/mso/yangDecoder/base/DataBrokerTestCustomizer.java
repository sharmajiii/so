/*-
 * ============LICENSE_START=======================================================
 * ONAP - SO
 * ================================================================================
 * Copyright (C) 2017 AT&T Intellectual Property. All rights reserved.
 * ================================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ============LICENSE_END=========================================================
 */

package org.openecomp.mso.yangDecoder.base;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import javassist.ClassPool;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.NotificationPublishService;
import org.opendaylight.mdsal.binding.api.NotificationService;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMDataBrokerAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMNotificationPublishServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.BindingDOMNotificationServiceAdapter;
import org.opendaylight.mdsal.binding.dom.adapter.BindingToNormalizedNodeCodec;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMNotificationRouter;
import org.opendaylight.mdsal.dom.broker.SerializedDOMDataBroker;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStore;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.DataObjectSerializerGenerator;
import org.opendaylight.yangtools.binding.data.codec.gen.impl.StreamWriterGenerator;
import org.opendaylight.yangtools.binding.data.codec.impl.BindingNormalizedNodeCodecRegistry;
import org.opendaylight.mdsal.binding.generator.impl.GeneratedClassLoadingStrategy;
import org.opendaylight.mdsal.binding.generator.util.JavassistUtils;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class DataBrokerTestCustomizer {

    private DOMDataBroker domDataBroker;
    private final DOMNotificationRouter domNotificationRouter;
    private final MockSchemaService schemaService;
    private ImmutableMap<LogicalDatastoreType, DOMStore> datastores;
    private final BindingToNormalizedNodeCodec bindingToNormalized;

    public ImmutableMap<LogicalDatastoreType, DOMStore> createDatastores() {
        return ImmutableMap.<LogicalDatastoreType, DOMStore>builder()
                .put(LogicalDatastoreType.OPERATIONAL, createOperationalDatastore())
                .put(LogicalDatastoreType.CONFIGURATION,createConfigurationDatastore())
                .build();
    }

    public DataBrokerTestCustomizer() {
        schemaService = new MockSchemaService();
        final ClassPool pool = ClassPool.getDefault();
        final DataObjectSerializerGenerator generator = StreamWriterGenerator.create(JavassistUtils.forClassPool(pool));
        final BindingNormalizedNodeCodecRegistry codecRegistry = new BindingNormalizedNodeCodecRegistry(generator);
        final GeneratedClassLoadingStrategy loading = GeneratedClassLoadingStrategy.getTCCLClassLoadingStrategy();
        bindingToNormalized = new BindingToNormalizedNodeCodec(loading, codecRegistry);
        schemaService.registerSchemaContextListener(bindingToNormalized);
        domNotificationRouter = DOMNotificationRouter.create(16);
    }

    public DOMStore createConfigurationDatastore() {
        final InMemoryDOMDataStore store = new InMemoryDOMDataStore("CFG", MoreExecutors.sameThreadExecutor());
        schemaService.registerSchemaContextListener(store);
        return store;
    }

    public DOMStore createOperationalDatastore() {
        final InMemoryDOMDataStore store = new InMemoryDOMDataStore("OPER", MoreExecutors.sameThreadExecutor());
        schemaService.registerSchemaContextListener(store);
        return store;
    }

    public DOMDataBroker createDOMDataBroker() {
        return new SerializedDOMDataBroker(getDatastores(), getCommitCoordinatorExecutor());
    }

    public NotificationService createNotificationService() {
        return new BindingDOMNotificationServiceAdapter(bindingToNormalized.getCodecRegistry(), domNotificationRouter);
    }

    public NotificationPublishService createNotificationPublishService() {
        return new BindingDOMNotificationPublishServiceAdapter(bindingToNormalized, domNotificationRouter);
    }


    public ListeningExecutorService getCommitCoordinatorExecutor() {
        return MoreExecutors.sameThreadExecutor();
    }

    public DataBroker createDataBroker() {
        return new BindingDOMDataBrokerAdapter(getDOMDataBroker(), bindingToNormalized);
    }

    public BindingToNormalizedNodeCodec getBindingToNormalized() {
        return bindingToNormalized;
    }

    public DOMSchemaService getSchemaService() {
        return schemaService;
    }

    private DOMDataBroker getDOMDataBroker() {
        if(domDataBroker == null) {
            domDataBroker = createDOMDataBroker();
        }
        return domDataBroker;
    }

    private synchronized ImmutableMap<LogicalDatastoreType, DOMStore> getDatastores() {
        if (datastores == null) {
            datastores = createDatastores();
        }
        return datastores;
    }

    public void updateSchema(final SchemaContext ctx) {
        schemaService.changeSchema(ctx);
    }

    public DOMNotificationRouter getDomNotificationRouter() {
        return domNotificationRouter;
    }
    public void close()
    {
    	if(bindingToNormalized!=null)bindingToNormalized.close();
    	if(domNotificationRouter!=null)domNotificationRouter.close();
    }
}
