EMC library for encoding/decoding object content (i.e. encrypting and/or compressing)
===

implements the EMC client encryption standard (https://community.emc.com/docs/DOC-34465)

Basic Usage
---

Encryption:

```java
    // you manage your own keys; you can optionally implement your own KeyProvider based on any key management suite.
    // this example uses a keystore file named my_keystore.jks
    KeyStore keystore = KeyStore.getInstance("jks");
    keystore.load(new FileInputStream("my_keystore.jks"), "my_keystore_password".toCharArray());
    KeyProvider keyProvider = new KeystoreKeyProvider(getKeystore(), "viprviprvipr".toCharArray(), "masterkey");

    // simply create the chain using default parameters
    CodecChain chain = new CodecChain(new EncryptionCodec()).withProperty(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);
    
    // now encode your object. you must provide a map of metadata (it can be empty), which will contain all of the
    // encoding information necessary to decode the object. this metadata should be stored along with your object.
    // this example uses S3 (via the AWS SDK)
    Map<String, String> metadata = new HashMap<String, String>();
    s3client.putObject(myBucket, myKey, chain.getEncodeStream(myRawObjectStream, metadata), null);
    
    // some encode metadata is not available until after the object is streamed. be *sure* to update the object with
    // the complete set of encode metadata or you may not be able to decode it!
    CopyObjectRequest request = new CopyObjectRequest(myBucket, myKey, myBucket, myKey);
    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setUserMetadata(metadata);
    request.setNewObjectMetadata(objectMetadata);
    s3client.copyObject(request);
```

Compression:

```java
    // simply create the chain using default parameters
    CodecChain chain = new CodecChain(new DeflateCodec());
    
    // now encode your object. you must provide a map of metadata (it can be empty), which will contain all of the
    // encoding information necessary to decode the object. this metadata should be stored along with your object.
    // this example uses S3 (via the AWS SDK)
    Map<String, String> metadata = new HashMap<String, String>();
    s3client.putObject(myBucket, myKey, chain.getEncodeStream(myRawObjectStream, metadata), null);
    
    // some encode metadata is not available until after the object is streamed. be *sure* to update the object with
    // the complete set of encode metadata or you may not be able to decode it!
    CopyObjectRequest request = new CopyObjectRequest(myBucket, myKey, myBucket, myKey);
    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setUserMetadata(metadata);
    request.setNewObjectMetadata(objectMetadata);
    s3client.copyObject(request);
```

Compression and encryption:

```java
    // you manage your own keys; you can optionally implement your own KeyProvider based on any key management suite.
    // this example uses a keystore file named my_keystore.jks
    KeyStore keystore = KeyStore.getInstance("jks");
    keystore.load(new FileInputStream("my_keystore.jks"), "my_keystore_password".toCharArray());
    KeyProvider keyProvider = new KeystoreKeyProvider(getKeystore(), "viprviprvipr".toCharArray(), "masterkey");

    // simply create the chain using default parameters
    CodecChain chain = new CodecChain(new DeflateCodec(), new EncryptionCodec())
            .withProperty(EncryptionCodec.PROP_KEY_PROVIDER, keyProvider);
    
    // now encode your object. you must provide a map of metadata (it can be empty), which will contain all of the
    // encoding information necessary to decode the object. this metadata should be stored along with your object.
    // this example uses S3 (via the AWS SDK)
    Map<String, String> metadata = new HashMap<String, String>();
    s3client.putObject(myBucket, myKey, chain.getEncodeStream(myRawObjectStream, metadata), null);
    
    // some encode metadata is not available until after the object is streamed. be *sure* to update the object with
    // the complete set of encode metadata or you may not be able to decode it!
    CopyObjectRequest request = new CopyObjectRequest(myBucket, myKey, myBucket, myKey);
    ObjectMetadata objectMetadata = new ObjectMetadata();
    objectMetadata.setUserMetadata(metadata);
    request.setNewObjectMetadata(objectMetadata);
    s3client.copyObject(request);
```

Note: when adding multiple plugins to a chain, they will be ordered based on pre-determined priority (compression
before encryption) and you may not apply more than one plugin of the same type (compression or encryption) to the same
object (this would overwrite some encode metadata and make the object unreadable).