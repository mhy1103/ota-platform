package com.ota.platform.storage;

import com.ota.platform.config.OtaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class StorageConfig {

    @Bean
    public SwitchableBlobStore blobStore(OtaProperties props) throws IOException {
        return new SwitchableBlobStore(create(props.getStorage().isS3(), props, props.getStorage().getS3()));
    }

    static BlobStore create(boolean s3, OtaProperties props, OtaProperties.S3 cfg) throws IOException {
        return s3 ? new S3BlobStore(cfg) : new LocalBlobStore(props);
    }
}
