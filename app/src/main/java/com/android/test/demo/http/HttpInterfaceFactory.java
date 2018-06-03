package com.android.test.demo.http;


import android.util.Log;

import com.android.test.demo.App;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.java8.Java8CallAdapterFactory;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public final class HttpInterfaceFactory {
    private static final String TAG = "HttpInterfaceFactory";

    /**
     * 标识返回的数据是Json格式
     */
    public static final String TYPE_JSON = "json";

    /**
     * 标识返回的数据是bitmap
     */
    public static final String TYPE_BITMAP = "bitmap";

    private Map<String, OkHttpClient> mClients = new ConcurrentHashMap<>(4);
    private Map<Class<?>, Object> mInterfaces = new ConcurrentHashMap<>(10);
    private static HttpInterfaceFactory INSTANCE = new HttpInterfaceFactory();

    public static GithubInterfaces getGitHubHttpInterface() {
        return create(GithubInterfaces.class, TYPE_JSON);
    }

    public static SinaInterfaces getSinaHttpInterfaces() {
        return create(SinaInterfaces.class, TYPE_JSON);
    }

    public static BitmapInterfaces getBitmapInterfaces() {
        return create(BitmapInterfaces.class, TYPE_BITMAP);
    }

    public static <T> T create(Class<T> interfaces, String type) {
        Object service = INSTANCE.mInterfaces.get(interfaces);
        if (service != null) {
            return (T) service;
        }
        synchronized (INSTANCE.mInterfaces) {
            service = INSTANCE.mInterfaces.get(interfaces);
            if (service == null) {
                String baseUrl = "http://localhost/";
                if(interfaces.isAnnotationPresent(BaseUrl.class)) {
                    BaseUrl nameInject = interfaces.getAnnotation(BaseUrl.class);
                    baseUrl = nameInject.value();
                }
                Log.i(TAG, "create base url: " + baseUrl);
                Retrofit.Builder builder = new Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .client(INSTANCE.getClient(type))
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .addCallAdapterFactory(Java8CallAdapterFactory.create());
                if (type.equals(TYPE_JSON)) {
                    builder.addConverterFactory(GsonConverterFactory.create());
                } else if (type.equals(TYPE_BITMAP)) {
                    builder.addConverterFactory(new BitmapConverterFactory());
                }
                service = builder.build().create(interfaces);

                INSTANCE.mInterfaces.put(interfaces, service);
            }
        }
        return (T) service;
    }



    private OkHttpClient getClient(String type) {
        OkHttpClient client = mClients.get(type);
        if (client != null) {
            return client;
        }
        synchronized (mClients) {
            client = mClients.get(type);
            if (client == null) {
                client = createHttpClinet(type);
                mClients.put(type, client);
            }
        }
        return client;
    }
    private OkHttpClient createHttpClinet(String type) {
        HttpLoggingInterceptor loggingInterceptor =
                new HttpLoggingInterceptor(new HttpLoggingInterceptor.Logger() {
                    @Override
                    public void log(String message) {
                        Log.i(TAG, message);
                    }
                });
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);

        final long cacheSize = type.equals(TYPE_JSON) ? 1024 * 1024 * 20 : 1024 * 1024 * 50;
        final File cachePath = new File(App.getContext().getExternalCacheDir(), type);
        Log.i(TAG, "cache path: " + cachePath.getAbsolutePath());


        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                //.retryOnConnectionFailure(true)
                .addNetworkInterceptor(loggingInterceptor)
                //.addInterceptor(new CacheInterceptor())
                .addNetworkInterceptor(new CacheInterceptor())
                .cache(new Cache(cachePath, cacheSize))
                .build();

        return client;
    }
}
