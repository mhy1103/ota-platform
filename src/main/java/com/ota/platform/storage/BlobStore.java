package com.ota.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** 按 SHA-256 存取文件。 */
public interface BlobStore {

    boolean exists(String sha256);

    /** 把已经校验过哈希的临时文件存进来（本机存储会移动这个文件） */
    void put(String sha256, Path verifiedFile) throws IOException;

    /** 读取文件内容；不存在时抛 java.nio.file.NoSuchFileException */
    InputStream open(String sha256) throws IOException;

    /** 删除文件，不存在时什么都不做 */
    void delete(String sha256) throws IOException;

    /** 文件在本机的路径；不在本机（比如对象存储）时返回 null */
    Path localPath(String sha256);

    /** 写进清单的下载地址 */
    String publicUrl(String sha256);

    /** 后台显示用的说明 */
    String describe();
}
