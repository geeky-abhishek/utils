package com.uci.utils.cdn.samagra;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.uci.utils.bot.util.FileUtil;
import com.uci.utils.cdn.FileCdnProvider;
import io.minio.UploadObjectArgs;
import okhttp3.*;
import org.json.JSONObject;
import org.json.XML;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inversoft.error.Errors;
import com.inversoft.rest.ClientResponse;
import com.uci.utils.cache.service.RedisCacheService;

import io.fusionauth.domain.api.LoginResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.credentials.StaticProvider;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.InvalidResponseException;
import io.minio.errors.ServerException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@SuppressWarnings("ALL")
@Service
@Slf4j
@AllArgsConstructor
@Getter
@Setter
public class MinioClientService implements FileCdnProvider {
    private MinioClientProp minioClientProp;
    private RedisCacheService redisCacheService;

    /**
     * Get File Signed URL
     *
     * @param name
     * @return
     */
    public String getFileSignedUrl(String name) {
        String url = "";
        try {
            MinioClient minioClient = getMinioClient();
            if (minioClient != null) {
                try {
                    url = minioClient.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(minioClientProp.bucketId)
                                    .object(name)
                                    .expiry(1, TimeUnit.DAYS)
                                    .build()
                    );
                } catch (InvalidKeyException | InsufficientDataException | InternalException
                         | InvalidResponseException | NoSuchAlgorithmException | XmlParserException | ServerException
                         | IllegalArgumentException | IOException e) {
                    // TODO Auto-generated catch block
                    log.error("Exception in getCdnSignedUrl: " + e.getMessage());
                } catch (ErrorResponseException e1) {
                    log.error("Exception in getFileSignedUrl: " + e1.getMessage() + ", name: " + e1.getClass());
                }
            }
            log.info("minioClient url: " + url);
        } catch (Exception ex) {
            log.error("Exception in getFileSignedUrl: " + ex.getMessage());
        }

        return url;
    }

    public String uploadFile(String urlStr, String mimeType, String name, Double maxSizeForMedia) {
        try {
            MinioClient minioClient = getMinioClient();
            if (minioClient != null) {
                try {
                    /* Find File Name */
                    Path path = new File(urlStr).toPath();
                    String ext = FileUtil.getFileTypeByMimeSubTypeString(MimeTypeUtils.parseMimeType(mimeType).getSubtype());

                    Random rand = new Random();
                    if (name == null || name.isEmpty()) {
                        name = UUID.randomUUID().toString();
                    }
                    name += "." + ext;

                    log.info("Minio CDN File Name :" + name);

                    /* File input stream to copy from */
                    URL url = new URL(urlStr);
                    byte[] inputBytes = url.openStream().readAllBytes();

                    /* Discard if file size is greater than MAX_SIZE_FOR_MEDIA */
                    if (maxSizeForMedia != null && inputBytes.length > maxSizeForMedia) {
                        log.info("file size is greater than limit : " + inputBytes.length);
                        return "";
                    }

                    /* Create temp file to copy to */
                    String localPath = "/tmp/";
                    String filePath = localPath + name;
                    File temp = new File(filePath);
                    temp.createNewFile();

                    // Copy file from url to temp file
                    Files.copy(new ByteArrayInputStream(inputBytes), Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);

                    minioClient.uploadObject(
                            UploadObjectArgs.builder()
                                    .bucket(minioClientProp.bucketId)
                                    .object(name)
                                    .filename(filePath)
                                    .build());

                    // Delete temp file
                    temp.delete();

                    return name;
                } catch (InvalidKeyException | InsufficientDataException | InternalException
                         | InvalidResponseException | NoSuchAlgorithmException | XmlParserException | ServerException
                         | IllegalArgumentException | IOException e) {
                    // TODO Auto-generated catch block
                    log.error("Exception in getCdnSignedUrl: " + e.getMessage());
                } catch (ErrorResponseException e1) {
                    log.error("Exception in getFileSignedUrl: " + e1.getMessage() + ", name: " + e1.getClass());
                }
            }
        } catch (Exception ex) {
            log.error("Exception in getFileSignedUrl: " + ex.getMessage());
        }

        return "";
    }

    public String uploadFileFromInputStream(InputStream binary, String mimeType, String name) {
        try {
            MinioClient minioClient = getMinioClient();
            if (minioClient != null) {
                File temp = null;
                try {
                    String ext = FileUtil.getFileTypeByMimeSubTypeString(MimeTypeUtils.parseMimeType(mimeType).getSubtype());

                    Random rand = new Random();
                    if (name == null || name.isEmpty()) {
                        name = UUID.randomUUID().toString();
                    }
                    name += "." + ext;

                    log.info("Minio CDN File Name :" + name);

                    /* File input stream to copy from */
                    byte[] inputBytes = binary.readAllBytes();


                    /* Create temp file to copy to */
                    String localPath = "/tmp/";
                    String filePath = localPath + name;
                    temp = new File(filePath);
                    temp.createNewFile();

                    // Copy file from url to temp file
                    Files.copy(new ByteArrayInputStream(inputBytes), Paths.get(filePath), StandardCopyOption.REPLACE_EXISTING);

                    minioClient.uploadObject(
                            UploadObjectArgs.builder()
                                    .bucket(minioClientProp.bucketId)
                                    .object(name)
                                    .filename(filePath)
                                    .build());


                    return name;

                } catch (InvalidKeyException | InsufficientDataException | InternalException
                         | InvalidResponseException | NoSuchAlgorithmException | XmlParserException | ServerException
                         | IllegalArgumentException | IOException e) {
                    // TODO Auto-generated catch block
                    log.error("Exception in getCdnSignedUrl: " + e.getMessage());
                } catch (ErrorResponseException e1) {
                    log.error("Exception in getFileSignedUrl: " + e1.getMessage() + ", name: " + e1.getClass());
                } finally {
                    // Delete temp file
                    if (temp != null && temp.exists()) {
                        temp.delete();
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Exception in uploadFileFromInputStream: " + ex.getMessage());
        }
        return "";
    }

    /**
     * Get Signed URL of CDN for given media file name
     *
     * @param mediaName
     * @return
     */
    public String getCdnSignedUrl(String name) {
        return getFileSignedUrl(name);
    }

    /**
     * Get Minio Client
     *
     * @return
     */
    private MinioClient getMinioClient() {
        if (minioClientProp != null) {
            try {
                StaticProvider provider = getMinioCredentialsProvider();
                log.info("provider: " + provider + ", url: " + minioClientProp.cdnBaseUrl);
                if (provider != null) {
                    return MinioClient.builder()
                            .endpoint(minioClientProp.cdnBaseUrl)
                            .credentialsProvider(provider)
                            .build();
                }
            } catch (Exception e) {
                log.error("Exception in getMinioClient with cache: " + e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get Credentials Provider for Minio Client
     *
     * @return
     */
    private StaticProvider getMinioCredentialsProvider() {
        try {
            /* Get credentials in cache */
            Map<String, String> cacheData = getMinioCredentialsCache();
            if (cacheData.get("sessionToken") != null && cacheData.get("accessKey") != null && cacheData.get("secretAccessKey") != null) {
                return new StaticProvider(cacheData.get("accessKey"), cacheData.get("secretAccessKey"), cacheData.get("sessionToken"));
            }

            String token = getFusionAuthToken();
            log.info("token: " + token);
            if (!token.isEmpty()) {
                Integer duration = 36000;
                OkHttpClient client = new OkHttpClient().newBuilder().connectTimeout(90, TimeUnit.SECONDS)
                        .writeTimeout(90, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS).build();
                MediaType mediaType = MediaType.parse("application/json");

                UriComponents builder = UriComponentsBuilder.fromHttpUrl(minioClientProp.cdnBaseUrl)
                        .queryParam("Action", "AssumeRoleWithWebIdentity")
                        .queryParam("DurationSeconds", 36000) //duration: 10 Hours
                        .queryParam("WebIdentityToken", token)
                        .queryParam("Version", "2011-06-15")
                        .build();
                URI expanded = URI.create(builder.toUriString());
                RequestBody body = RequestBody.create(mediaType, "");
                Request request = new Request.Builder().url(expanded.toString()).method("POST", body)
                        .addHeader("Content-Type", "application/json").build();

                try {
                    Response callResponse = client.newCall(request).execute();
                    String response = callResponse.body().string();

                    JSONObject xmlJSONObj = XML.toJSONObject(response);
                    String jsonPrettyPrintString = xmlJSONObj.toString(4);

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(jsonPrettyPrintString);
                    JsonNode credentials = node.path("AssumeRoleWithWebIdentityResponse").path("AssumeRoleWithWebIdentityResult").path("Credentials");
                    if (credentials != null && credentials.get("SessionToken") != null
                            && credentials.get("AccessKeyId") != null && credentials.get("SecretAccessKey") != null) {
                        String sessionToken = credentials.get("SessionToken").asText();
                        String accessKey = credentials.get("AccessKeyId").asText();
                        String secretAccessKey = credentials.get("SecretAccessKey").asText();

                        log.info("sessionToken: " + sessionToken + ", accessKey: " + accessKey + ",secretAccessKey: " + secretAccessKey);

                        if (!accessKey.isEmpty() && !secretAccessKey.isEmpty() && !sessionToken.isEmpty()) {
                            /* Set credentials in cache */
                            setMinioCredentialsCache(sessionToken, accessKey, secretAccessKey);

                            return new StaticProvider(accessKey, secretAccessKey, sessionToken);
                            //						return new StaticProvider("test", secretAccessKey, sessionToken);
                        }
                    } else {
                        if (node.path("ErrorResponse") != null
                                && node.path("ErrorResponse").path("Error") != null
                                && node.path("ErrorResponse").path("Error").path("Message") != null) {
                            log.error("Error when getting credentials for minio client: " + node.path("ErrorResponse").path("Error").path("Message").asText());
                        }
                    }
                } catch (IOException e) {
                    log.error("IOException in getMinioCredentialsProvider for request call: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Exception in getMinioCredentialsProvider: " + e.getMessage());
        }
        return null;
    }

    /**
     * Set Minio Credentials Cache
     *
     * @param sessionToken
     * @param accessKey
     * @param secretAccessKey
     */
    private void setMinioCredentialsCache(String sessionToken, String accessKey, String secretAccessKey) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        /* local date time */
        LocalDateTime localTomorrow = LocalDateTime.now().plusDays(1);
        String expiryDateString = fmt.format(localTomorrow).toString();

        redisCacheService.setMinioCDNCache("sessionToken", sessionToken);
        redisCacheService.setMinioCDNCache("accessKey", accessKey);
        redisCacheService.setMinioCDNCache("secretAccessKey", secretAccessKey);
        redisCacheService.setMinioCDNCache("expiresAt", expiryDateString);
    }

    /**
     * Get Minio Credentials from Redis Cache
     *
     * @param sessionToken
     * @param accessKey
     * @param secretAccessKey
     * @return
     */
    private Map<String, String> getMinioCredentialsCache() {
        Map<String, String> credentials = new HashMap();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        /* local date time */
        LocalDateTime localNow = LocalDateTime.now();
        /* Expiry Date time */
        String expiry = (String) redisCacheService.getMinioCDNCache("expiresAt");
        if (expiry != null) {
            LocalDateTime expiryDateTime = LocalDateTime.parse(expiry, fmt);

            if (localNow.compareTo(expiryDateTime) < 0) {
                credentials.put("sessionToken", (String) redisCacheService.getMinioCDNCache("sessionToken"));
                credentials.put("accessKey", (String) redisCacheService.getMinioCDNCache("accessKey"));
                credentials.put("secretAccessKey", (String) redisCacheService.getMinioCDNCache("secretAccessKey"));
            }
        }
        return credentials;
    }

    /**
     * Get Fustion Auth Token
     *
     * @return
     */
    private String getFusionAuthToken() {
        String token = "";
        try {
            ClientResponse<LoginResponse, Errors> clientResponse = minioClientProp.fusionAuth.login(minioClientProp.loginRequest);
            if (clientResponse.wasSuccessful()) {
                token = clientResponse.successResponse.token;
            }
        } catch (Exception e) {
            log.error("Exception in getFusionAuthToken: " + e.getMessage());
        }
        return token;
    }
}
