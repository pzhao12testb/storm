/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.storm.common;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.math3.util.Pair;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.storm.security.INimbusCredentialPlugin;
import org.apache.storm.security.auth.ICredentialsRenewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The base class that for auto credential plugins that abstracts out some of the common functionality.
 */
public abstract class AbstractHadoopNimbusPluginAutoCreds
    implements INimbusCredentialPlugin, ICredentialsRenewer, CredentialKeyProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractHadoopNimbusPluginAutoCreds.class);
    public static final String CONFIG_KEY_RESOURCES = "resources";

    @Override
    public void prepare(Map conf) {
        doPrepare(conf);
    }

    @Override
    public void populateCredentials(Map<String, String> credentials, Map<String, Object> topologyConf) {
        try {
            List<String> configKeys = getConfigKeys(topologyConf);
            if (!configKeys.isEmpty()) {
                for (String configKey : configKeys) {
                    credentials.put(getCredentialKey(configKey),
                            DatatypeConverter.printBase64Binary(getHadoopCredentials(topologyConf, configKey)));
                }
            } else {
                credentials.put(getCredentialKey(StringUtils.EMPTY),
                        DatatypeConverter.printBase64Binary(getHadoopCredentials(topologyConf)));
            }
            LOG.info("Tokens added to credentials map.");
        } catch (Exception e) {
            LOG.error("Could not populate credentials.", e);
        }
    }

    @Override
    public void renew(Map<String, String> credentials, Map<String, Object> topologyConf) {
        doRenew(credentials, topologyConf);
    }

    protected Set<Pair<String, Credentials>> getCredentials(Map<String, String> credentials,
        List<String> configKeys) {
        return HadoopCredentialUtil.getCredential(this, credentials, configKeys);
    }

    protected void fillHadoopConfiguration(Map topologyConf, String configKey, Configuration configuration) {
        Map<String, Object> config = (Map<String, Object>) topologyConf.get(configKey);
        LOG.info("TopoConf {}, got config {}, for configKey {}", topologyConf, config, configKey);
        if (config != null) {
            List<String> resourcesToLoad = new ArrayList<>();
            for (Map.Entry<String, Object> entry : config.entrySet()) {
                if (entry.getKey().equals(CONFIG_KEY_RESOURCES)) {
                    resourcesToLoad.addAll((List<String>) entry.getValue());
                } else {
                    configuration.set(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            LOG.info("Resources to load {}", resourcesToLoad);
            // add configs from resources like hdfs-site.xml
            for (String pathStr : resourcesToLoad) {
                configuration.addResource(new Path(Paths.get(pathStr).toUri()));
            }
        }
        LOG.info("Initializing UGI with config {}", configuration);
        UserGroupInformation.setConfiguration(configuration);
    }

    /**
     * Prepare the plugin
     *
     * @param conf the storm cluster conf set via storm.yaml
     */
    protected abstract void doPrepare(Map conf);

    /**
     * The lookup key for the config key string
     *
     * @return the config key string
     */
    protected abstract String getConfigKeyString();

    protected abstract byte[] getHadoopCredentials(Map topologyConf, String configKey);

    protected abstract byte[] getHadoopCredentials(Map topologyConf);

    protected abstract void doRenew(Map<String, String> credentials, Map topologyConf);

    protected List<String> getConfigKeys(Map conf) {
        String configKeyString = getConfigKeyString();
        return (List<String>) conf.get(configKeyString);
    }

}
