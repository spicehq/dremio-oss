/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
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
package com.dremio.exec.store.hive;

import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import com.dremio.exec.catalog.conf.ConnectionConf;
import com.dremio.exec.catalog.conf.DefaultCtasFormatSelection;
import com.dremio.exec.catalog.conf.DisplayMetadata;
import com.dremio.exec.catalog.conf.DoNotDisplay;
import com.dremio.exec.catalog.conf.NotMetadataImpacting;
import com.dremio.exec.catalog.conf.Property;
import com.dremio.exec.store.StoragePlugin;
import com.dremio.exec.store.dfs.MutablePluginConf;

import io.protostuff.Tag;

/**
 * Base configuration for the Hive storage plugin
 */
public abstract class BaseHiveStoragePluginConfig<T extends ConnectionConf<T, P>, P extends StoragePlugin> extends ConnectionConf<T, P>
  implements MutablePluginConf {
  /*
   * Hostname where Hive metastore server is running
   */
  @Tag(1)
  @DisplayMetadata(label = "Hive Metastore Host")
  public String hostname;

  /*
   * Listening port of Hive metastore server
   */
  @Tag(2)
  @Min(1)
  @Max(65535)
  @DisplayMetadata(label = "Port")
  public int port = 9083;

  /*
   * Is kerberos authentication enabled on metastore services?
   */
  @Tag(3)
  @DisplayMetadata(label = "Enable SASL")
  public boolean enableSasl = false;

  /*
   * Kerberos principal name of metastore servers if kerberos authentication is enabled
   */
  @Tag(4)
  @DisplayMetadata(label = "Hive Kerberos Principal")
  public String kerberosPrincipal;

  /*
   * List of configuration properties.
   */
  @Tag(5)
  public List<Property> propertyList;

  @Tag(11)
  @NotMetadataImpacting
  @DisplayMetadata(label = "Enable asynchronous access for Parquet datasets")
  public boolean enableAsync = true;

  @Tag(12)
  @NotMetadataImpacting
  @DisplayMetadata(label = "Enable local caching for Amazon S3 and Azure Storage datasets")
  public boolean isCachingEnabledForS3AndAzureStorage = true;

  @Tag(13)
  @NotMetadataImpacting
  @DisplayMetadata(label = "Enable local caching for HDFS")
  public boolean isCachingEnabledForHDFS = false;

  @Tag(14)
  @NotMetadataImpacting
  @Min(value = 1, message = "Max percent of total available cache space must be between 1 and 100")
  @Max(value = 100, message = "Max percent of total available cache space must be between 1 and 100")
  @DisplayMetadata(label = "Max percent of total available cache space to use when possible")
  public int maxCacheSpacePct = 100;


  @Tag(15)
  @NotMetadataImpacting
  @DoNotDisplay
  @DisplayMetadata(label = "Default CTAS Format")
  public DefaultCtasFormatSelection defaultCtasFormat = DefaultCtasFormatSelection.ICEBERG;

  @Override
  public String getDefaultCtasFormat() {
    return defaultCtasFormat.getDefaultCtasFormat();
  }

  /**
   * Gets the plugin identifier for the PF4J module. This can return null if no external bundle is to be used,
   * eg it uses a plugin defined within the current classloader.
   */
  @Nullable
  public abstract String getPf4jPluginId();
}
