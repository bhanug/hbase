/*
 * Copyright 2011 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.util;

import java.io.DataInput;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HConstants;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.io.hfile.CacheConfig;
import org.apache.hadoop.hbase.io.hfile.HFile;
import org.apache.hadoop.hbase.regionserver.StoreFile;
import org.apache.hadoop.hbase.regionserver.StoreFile.BloomType;

/**
 * Handles Bloom filter initialization based on configuration and serialized
 * metadata in the reader and writer of {@link StoreFile}.
 */
public final class BloomFilterFactory {

  private static final Log LOG =
      LogFactory.getLog(BloomFilterFactory.class.getName());

  /** This class should not be instantiated. */
  private BloomFilterFactory() {}

  /**
   * Specifies the target error rate to use when selecting the number of keys
   * per Bloom filter.
   */
  public static final String IO_STOREFILE_BLOOM_ERROR_RATE =
      "io.storefile.bloom.error.rate";

  /**
   * Maximum folding factor allowed. The Bloom filter will be shrunk by
   * the factor of up to 2 ** this times if we oversize it initially.
   */
  public static final String IO_STOREFILE_BLOOM_MAX_FOLD =
      "io.storefile.bloom.max.fold";

  /**
   * For default (single-block) Bloom filters this specifies the maximum number
   * of keys.
   */
  public static final String IO_STOREFILE_BLOOM_MAX_KEYS =
      "io.storefile.bloom.max.keys";

  /** Master switch to enable Bloom filters */
  public static final String IO_STOREFILE_BLOOM_ENABLED =
      "io.storefile.bloom.enabled";

  /** Master switch to enable Delete Family Bloom filters */
  public static final String IO_STOREFILE_DELETEFAMILY_BLOOM_ENABLED =
      "io.storefile.delete.family.bloom.enabled";

  /** Master switch to enable Delete Column Family filters */
  public static final String IO_STOREFILE_DELETECOLUMN_BLOOM_ENABLED =
      "io.storefile.delete.column.bloom.enabled";

  /**
   * Target Bloom block size. Bloom filter blocks of approximately this size
   * are interleaved with data blocks.
   */
  public static final String IO_STOREFILE_BLOOM_BLOCK_SIZE =
      "io.storefile.bloom.block.size";

  /** Maximum number of times a Bloom filter can be "folded" if oversized */
  private static final int MAX_ALLOWED_FOLD_FACTOR = 7;

  /**
   * Instantiates the correct Bloom filter class based on the version provided
   * in the meta block data.
   *
   * @param meta the byte array holding the Bloom filter's metadata, including
   *          version information
   * @param reader the {@link HFile} reader to use to lazily load Bloom filter
   *          blocks
   * @return an instance of the correct type of Bloom filter
   * @throws IllegalArgumentException
   */
  public static BloomFilter
      createFromMeta(DataInput meta, HFile.Reader reader)
      throws IllegalArgumentException, IOException {
    int version = meta.readInt();
    switch (version) {
      case ByteBloomFilter.VERSION:
        // This is only possible in a version 1 HFile. We are ignoring the
        // passed comparator because raw byte comparators are always used
        // in version 1 Bloom filters.
        return new ByteBloomFilter(meta);

      case CompoundBloomFilterBase.VERSION:
        return new CompoundBloomFilter(meta, reader);

      default:
        throw new IllegalArgumentException(
          "Bad bloom filter format version " + version
        );
    }
  }

  /**
   * @return true if general Bloom (Row or RowCol) filters are enabled in the
   * given configuration
   */
  public static boolean isGeneralBloomEnabled(Configuration conf) {
    return conf.getBoolean(IO_STOREFILE_BLOOM_ENABLED, true);
  }

  /**
   * @return true if Delete Family Bloom filters are enabled in the given configuration
   */
  public static boolean isDeleteFamilyBloomEnabled(Configuration conf) {
    return conf.getBoolean(IO_STOREFILE_DELETEFAMILY_BLOOM_ENABLED, true);
  }

  /**
   * @return true if Delete Column Bloom filters are enabled in the given configuration
   */
  public static boolean isDeleteColumnBloomEnabled(Configuration conf) {
    return conf.getBoolean(IO_STOREFILE_DELETECOLUMN_BLOOM_ENABLED, true);
  }

  /**
   * @return the Bloom filter error rate in the given configuration
   */
  public static float getErrorRate(Configuration conf) {
    return conf.getFloat(IO_STOREFILE_BLOOM_ERROR_RATE, (float) 0.01);
  }

  /**
   * @return the value for Bloom filter max fold in the given configuration
   */
  public static int getMaxFold(Configuration conf) {
    return conf.getInt(IO_STOREFILE_BLOOM_MAX_FOLD, MAX_ALLOWED_FOLD_FACTOR);
  }

  /** @return the compound Bloom filter block size from the configuration */
  public static int getBloomBlockSize(Configuration conf) {
    return conf.getInt(IO_STOREFILE_BLOOM_BLOCK_SIZE, 128 * 1024);
  }

  /**
  * @return max key for the Bloom filter from the configuration
  */
  public static int getMaxKeys(Configuration conf) {
    return conf.getInt(IO_STOREFILE_BLOOM_MAX_KEYS, 128 * 1000 * 1000);
  }

   /**
   * Copy the BloomFilter related configuration from fromConf to toConf
   * @param fromConf conf we will copy from
   * @param toConf conf we will copy to
   */
  public static void copyBloomFilterConf(Configuration fromConf, Configuration toConf) {
    toConf.setBoolean(IO_STOREFILE_BLOOM_ENABLED, isGeneralBloomEnabled(fromConf));
    toConf.setFloat(IO_STOREFILE_BLOOM_ERROR_RATE, getErrorRate(fromConf));
    toConf.setInt(IO_STOREFILE_BLOOM_MAX_FOLD, getMaxFold(fromConf));
    toConf.setInt(IO_STOREFILE_BLOOM_BLOCK_SIZE, getBloomBlockSize(fromConf));
    toConf.setBoolean(CacheConfig.CACHE_BLOOM_BLOCKS_ON_WRITE_KEY,
        fromConf.getBoolean(CacheConfig.CACHE_BLOOM_BLOCKS_ON_WRITE_KEY,
            CacheConfig.DEFAULT_CACHE_BLOOMS_ON_WRITE));
    toConf.setInt(IO_STOREFILE_BLOOM_MAX_KEYS, getMaxKeys(fromConf));
  }

  /**
   * Creates a new general (Row or RowCol) Bloom filter at the time of
   * {@link org.apache.hadoop.hbase.regionserver.StoreFile} writing.
   *
   * @param conf
   * @param bloomType
   * @param maxKeys an estimate of the number of keys we expect to insert.
   *        Irrelevant if compound Bloom filters are enabled.
   * @param writer the HFile writer
   * @param bloomErrorRate
   * @return the new Bloom filter, or null in case Bloom filters are disabled
   *         or when failed to create one.
   */
  public static BloomFilterWriter createGeneralBloomAtWrite(
      Configuration conf, CacheConfig cacheConf, BloomType bloomType,
      int maxKeys, HFile.Writer writer, float bloomErrorRate) {
    if (!isGeneralBloomEnabled(conf)) {
      LOG.info("Bloom filters are disabled by configuration for "
          + writer.getPath()
          + (conf == null ? " (configuration is null)" : ""));
      return null;
    } else if (bloomType == BloomType.NONE) {
      LOG.info("Bloom filter is turned off for the column family");
      return null;
    }

    // In case of row/column Bloom filter lookups, each lookup is an OR if two
    // separate lookups. Therefore, if each lookup's false positive rate is p,
    // the resulting false positive rate is err = 1 - (1 - p)^2, and
    // p = 1 - sqrt(1 - err).
    if (bloomType == BloomType.ROWCOL) {
      bloomErrorRate = (float) (1 - Math.sqrt(1 - bloomErrorRate));
    }

    int maxFold = getMaxFold(conf);

    if (HFile.getFormatVersion(conf) > HFile.MIN_FORMAT_VERSION) {
      // In case of compound Bloom filters we ignore the maxKeys hint.
      CompoundBloomFilterWriter bloomWriter = new CompoundBloomFilterWriter(
          getBloomBlockSize(conf), bloomErrorRate, Hash.getHashType(conf), maxFold,
          cacheConf.shouldCacheBloomsOnWrite(), bloomType == BloomType.ROWCOL
              ? KeyValue.KEY_COMPARATOR : Bytes.BYTES_RAWCOMPARATOR);
      writer.addInlineBlockWriter(bloomWriter);
      return bloomWriter;
    } else {
      // A single-block Bloom filter. Only used when testing HFile format
      // version 1.
      int tooBig = getMaxKeys(conf);

      if (maxKeys <= 0) {
        LOG.warn("Invalid maximum number of keys specified: " + maxKeys
            + ", not using Bloom filter");
        return null;
      } else if (maxKeys < tooBig) {
        BloomFilterWriter bloom = new ByteBloomFilter(maxKeys, bloomErrorRate,
            Hash.getHashType(conf), maxFold);
        bloom.allocBloom();
        return bloom;
      } else {
        LOG.debug("Skipping bloom filter because max keysize too large: "
            + maxKeys);
      }
    }
    return null;
  }

  /**
   * Creates a new Delete Family/Column Bloom filter at the time of
   * {@link org.apache.hadoop.hbase.regionserver.StoreFile} writing.
   * @param conf
   * @param maxKeys an estimate of the number of keys we expect to insert.
   *        Irrelevant if compound Bloom filters are enabled.
   * @param writer the HFile writer
   * @param bloomErrorRate
   * @return the new Bloom filter, or null in case Bloom filters are disabled
   *         or when failed to create one.
   */
  public static BloomFilterWriter createDeleteBloomAtWrite(
      Configuration conf, CacheConfig cacheConf, int maxKeys,
      HFile.Writer writer, float bloomErrorRate, String deleteBloomType) {
    if (deleteBloomType.equals(HConstants.DELETE_FAMILY_BLOOM_FILTER) && !isDeleteFamilyBloomEnabled(conf)) {
      LOG.info("Delete Family Bloom filters are disabled by configuration for "
          + writer.getPath()
          + (conf == null ? " (configuration is null)" : ""));
      return null;
    } else if (deleteBloomType.equals(HConstants.DELETE_COLUMN_BLOOM_FILTER) && !isDeleteColumnBloomEnabled(conf)) {
      LOG.info("Delete Column Bloom filters are disabled by configuration for "
          + writer.getPath()
          + (conf == null ? " (configuration is null)" : ""));
      return null;
    }

    if (HFile.getFormatVersion(conf) > HFile.MIN_FORMAT_VERSION) {
      int maxFold = getMaxFold(conf);
      // In case of compound Bloom filters we ignore the maxKeys hint.
      CompoundBloomFilterWriter bloomWriter = new CompoundBloomFilterWriter(
          getBloomBlockSize(conf), bloomErrorRate, Hash.getHashType(conf),
          maxFold, cacheConf.shouldCacheBloomsOnWrite(),
          Bytes.BYTES_RAWCOMPARATOR);
      writer.addInlineBlockWriter(bloomWriter);
      return bloomWriter;
    } else {
      LOG.info("Delete Bloom filter is not supported in HFile V1");
      return null;
    }
  }
};
