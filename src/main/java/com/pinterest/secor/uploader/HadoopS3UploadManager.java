/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pinterest.secor.uploader;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pinterest.secor.common.LogFilePath;
import com.pinterest.secor.common.SecorConfig;
import com.pinterest.secor.util.FileUtil;

/**
 * Manages uploads to S3 using the Hadoop API.
 *
 * @author Pawel Garbacki (pawel@pinterest.com)
 */
public class HadoopS3UploadManager extends UploadManager {
    private static final Logger LOG = LoggerFactory.getLogger(HadoopS3UploadManager.class);

    private static final ExecutorService executor = Executors.newFixedThreadPool(256);

    private final String mSchema;

    public HadoopS3UploadManager(SecorConfig config) {
        super(config);
        mSchema = config.getSchema();
    }

    public Handle<?> upload(LogFilePath localPath) throws Exception {
        String prefix = FileUtil.getPrefix(localPath.getTopic(), mConfig);
        LogFilePath path = localPath.withPrefix(prefix);
        final String localLogFilename = localPath.getLogFilePath();
        final String logFileName;

        if (FileUtil.s3PathPrefixIsAltered(path.getLogFilePath(), mConfig)) {
           logFileName = localPath.withPrefix(FileUtil.getS3AlternativePrefix(mConfig)).withoutSchema(mSchema).getLogFilePath();
           LOG.info("Will upload file to alternative s3 prefix path {}", logFileName);
        }
        else {
            logFileName = path.withoutSchema(mSchema).getLogFilePath();
        }

        LOG.info("uploading file {} to {}", localLogFilename, logFileName);

        final Future<?> f = executor.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    FileUtil.moveToCloud(localLogFilename, logFileName);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return new FutureHandle(f);
    }
}
