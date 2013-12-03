
package com.microsoft.adal.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;

import java.util.GregorianCalendar;
import java.util.UUID;

import java.util.HashMap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.mock.MockContext;
import android.test.mock.MockPackageManager;
import android.util.Log;

import com.microsoft.adal.AuthenticationActivity;

import com.microsoft.adal.AuthenticationCallback;
import com.microsoft.adal.AuthenticationConstants;
import com.microsoft.adal.AuthenticationContext;
import com.microsoft.adal.AuthenticationException;
import com.microsoft.adal.CacheKey;
import com.microsoft.adal.DefaultTokenCacheStore;
import com.microsoft.adal.ErrorCodes.ADALError;
import com.microsoft.adal.HttpWebResponse;
import com.microsoft.adal.IDiscovery;
import com.microsoft.adal.ITokenCacheStore;
import com.microsoft.adal.TokenCacheItem;

import com.microsoft.adal.AuthenticationContext;
import com.microsoft.adal.ITokenCacheStore;
import com.microsoft.adal.MemoryTokenCacheStore;

public class AuthenticationContextTests extends AndroidTestCase {

    /**
     * check case-insensitive lookup
     */
    private static final String VALID_AUTHORITY = "https://Login.windows.net/Omercantest.Onmicrosoft.com";

    protected final static int CONTEXT_REQUEST_TIME_OUT = 20000;

    private final static String TEST_AUTHORITY = "http://login.windows.net/common";

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * test constructor to make sure authority parameter is set
     */
    public void testConstructor() {
        String authority = "authority";
        AuthenticationContext context = new AuthenticationContext(getContext(), authority, false);
        assertSame(authority, context.getAuthority());
    }

    /**
     * if package does not have declaration for activity, it should return false
     * 
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     * @throws ClassNotFoundException
     */
    public void testResolveIntent() throws IllegalArgumentException, IllegalAccessException,
            InvocationTargetException, ClassNotFoundException, NoSuchMethodException,
            InstantiationException {
        TestMockContext mockContext = new TestMockContext(getContext());
        AuthenticationContext context = new AuthenticationContext(mockContext, "authority", false);
        Method m = ReflectionUtils.getTestMethod(context, "resolveIntent", Intent.class);
        Intent intent = new Intent();
        intent.setClass(mockContext, AuthenticationActivity.class);

        boolean actual = (Boolean)m.invoke(context, intent);
        assertTrue("Intent is expected to resolve", actual);

        mockContext.resolveIntent = false;
        actual = (Boolean)m.invoke(context, intent);
        assertFalse("Intent is not expected to resolve", actual);
    }

    /**
     * Test throws for different missing arguments
     */
    public void testAcquireTokenNegativeArguments() {
        TestMockContext mockContext = new TestMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext, "authority",
                false);
        final MockActivity testActivity = new MockActivity();
        final MockAuthenticationCallback testEmptyCallback = new MockAuthenticationCallback();

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "callback",
                new Runnable() {

                    @Override
                    public void run() {
                        context.acquireToken(testActivity, "resource", "clientId", "redirectUri",
                                "userid", null);
                    }
                });

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "activity",
                new Runnable() {

                    @Override
                    public void run() {
                        context.acquireToken(null, "resource", "clientId", "redirectUri", "userid",
                                testEmptyCallback);
                    }
                });

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "resource",
                new Runnable() {

                    @Override
                    public void run() {
                        context.acquireToken(testActivity, null, "clientId", "redirectUri",
                                "userid", testEmptyCallback);
                    }
                });

        AssertUtils.assertThrowsException(IllegalArgumentException.class, "clientid",
                new Runnable() {

                    @Override
                    public void run() {
                        context.acquireToken(testActivity, "resource", null, "redirectUri",
                                "userid", testEmptyCallback);
                    }
                });

    }

    /**
     * authority is malformed and error should come back in callback
     * 
     * @throws InterruptedException
     */
    public void testAcquireTokenAuthorityMalformed() throws InterruptedException {
        // Malformed url error will come back in callback
        TestMockContext mockContext = new TestMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext, "authority",
                false);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        context.acquireToken(testActivity, "resource", "clientId", "redirectUri", "userid",
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNotNull("Error is not null", callback.mException);
        assertEquals("NOT_VALID_URL", ADALError.DEVELOPER_AUTHORITY_IS_NOT_VALID_URL,
                ((AuthenticationException)callback.mException).getCode());
    }

    /**
     * authority is validated and intent start request is sent
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void testAcquireTokenValidateAuthorityReturnsValid() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

        TestMockContext mockContext = new TestMockContext(getContext());
        AuthenticationContext context = new AuthenticationContext(mockContext, VALID_AUTHORITY,
                true);
        final CountDownLatch signal = new CountDownLatch(1);
        MockActivity testActivity = new MockActivity();
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockDiscovery discovery = new MockDiscovery(true);
        ReflectionUtils.setFieldValue(context, "mDiscovery", discovery);

        context.acquireToken(testActivity, "resource", "clientid", "redirectUri", "userid",
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNull("Error is null", callback.mException);
        assertEquals("Activity was attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);
    }

    /**
     * Invalid authority returns
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void testAcquireTokenValidateAuthorityReturnsInValid() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

        TestMockContext mockContext = new TestMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, true);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockDiscovery discovery = new MockDiscovery(false);
        ReflectionUtils.setFieldValue(context, "mDiscovery", discovery);

        context.acquireToken(testActivity, "resource", "clientid", "redirectUri", "userid",
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNotNull("Error is not null", callback.mException);
        assertEquals("NOT_VALID_URL", ADALError.DEVELOPER_AUTHORITY_IS_NOT_VALID_INSTANCE,
                ((AuthenticationException)callback.mException).getCode());
        assertTrue(
                "Activity was not attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW != testActivity.mStartActivityRequestCode);
        clearCache(context);
    }

    /**
     * acquire token without validation
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void testAcquireTokenWithoutValidation() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

        TestMockContext mockContext = new TestMockContext(getContext());
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        context.acquireToken(testActivity, "resource", "clientid", "redirectUri", "userid",
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNull("Error is null", callback.mException);
        assertEquals("Activity was attempted to start with request code",
                AuthenticationConstants.UIRequest.BROWSER_FLOW,
                testActivity.mStartActivityRequestCode);
        clearCache(context);
    }

    /**
     * acquire token uses refresh token, but web request returns error
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void testRefreshTokenWebRequestHasError() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

        TestMockContext mockContext = new TestMockContext(getContext());
        ITokenCacheStore mockCache = getCacheForRefreshToken();
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);

        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        webrequest.setReturnResponse(new HttpWebResponse(503, null, null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);

        context.acquireToken(testActivity, "resource", "clientid", "redirectUri", "userid",
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback result
        assertNotNull("Error is not null", callback.mException);
        assertNull("Cache is empty for this item",
                mockCache.getItem(CacheKey.createCacheKey(VALID_AUTHORITY, "resource", "clientId")));
        clearCache(context);
    }

    /**
     * acquire token using refresh token. All web calls are mocked. Refresh
     * token response must match to result and cache.
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void testRefreshTokenPositive() throws InterruptedException, IllegalArgumentException,
            NoSuchFieldException, IllegalAccessException {

        TestMockContext mockContext = new TestMockContext(getContext());
        ITokenCacheStore mockCache = getCacheForRefreshToken();

        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);

        final MockActivity testActivity = new MockActivity();
        CacheKey cachekey = CacheKey.createCacheKey(TEST_AUTHORITY, "resource", "clientid");
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);
        MockWebRequestHandler webrequest = new MockWebRequestHandler();
        String json = "{\"access_token\":\"TokenFortestRefreshTokenPositive\",\"token_type\":\"Bearer\",\"expires_in\":\"28799\",\"expires_on\":\"1368768616\",\"refresh_token\":\"refresh112\",\"scope\":\"*\"}";
        webrequest.setReturnResponse(new HttpWebResponse(200, json.getBytes(Charset
                .defaultCharset()), null));
        ReflectionUtils.setFieldValue(context, "mWebRequest", webrequest);

        // call acquire token which will try refresh token based on cache
        context.acquireToken(testActivity, "resource", "clientid", "redirectUri", "userid",
                callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // check response in callback
        assertNull("Error is null", callback.mException);
        assertNotNull("Cache is Not empty for this item",
                mockCache.getItem(CacheKey.createCacheKey(VALID_AUTHORITY, "resource", "clientId")));
        clearCache(context);
    }

    /**
     * authority and resource are case insensitive. Cache lookup will return
     * item from cache.
     * 
     * @throws InterruptedException
     * @throws IllegalArgumentException
     * @throws NoSuchFieldException
     * @throws IllegalAccessException
     */
    public void testAcquireTokenCacheLookup() throws InterruptedException,
            IllegalArgumentException, NoSuchFieldException, IllegalAccessException {

        TestMockContext mockContext = new TestMockContext(getContext());
        String tokenToTest = "accessToken=" + UUID.randomUUID();
        String resource = "Resource" + UUID.randomUUID();
        ITokenCacheStore mockCache = getValidCache(tokenToTest, resource);
        final AuthenticationContext context = new AuthenticationContext(mockContext,
                VALID_AUTHORITY, false, mockCache);
        final MockActivity testActivity = new MockActivity();
        final CountDownLatch signal = new CountDownLatch(1);
        testActivity.mSignal = signal;
        MockAuthenticationCallback callback = new MockAuthenticationCallback(signal);

        // acquire token call will return from cache
        context.acquireToken(testActivity, resource, "ClienTid", "redirectUri", "userid", callback);
        signal.await(CONTEXT_REQUEST_TIME_OUT, TimeUnit.MILLISECONDS);

        // Check response in callback
        assertNull("Error is null", callback.mException);
        assertEquals("Same token in response as in cache", tokenToTest,
                callback.mResult.getAccessToken());
        clearCache(context);
    }

    private ITokenCacheStore getCacheForRefreshToken() {
        DefaultTokenCacheStore cache = new DefaultTokenCacheStore(getContext());
        Calendar expiredTime = new GregorianCalendar();
        Log.d("Test", "Time now:" + expiredTime.toString());
        expiredTime.add(Calendar.MINUTE, -60);
        TokenCacheItem refreshItem = new TokenCacheItem();
        refreshItem.setAuthority(VALID_AUTHORITY);
        refreshItem.setResource("resource");
        refreshItem.setClientId("clientId");
        refreshItem.setAccessToken("accessToken");
        refreshItem.setRefreshToken("refreshToken=");
        refreshItem.setExpiresOn(expiredTime.getTime());
        cache.setItem(refreshItem);
        return cache;
    }

    private ITokenCacheStore getValidCache(String token, String resource) {
        DefaultTokenCacheStore cache = new DefaultTokenCacheStore(getContext());
        // Code response
        Calendar timeAhead = new GregorianCalendar();
        Log.d("Test", "Time now:" + timeAhead.toString());
        timeAhead.add(Calendar.MINUTE, 10);
        TokenCacheItem refreshItem = new TokenCacheItem();
        refreshItem.setAuthority(VALID_AUTHORITY);
        refreshItem.setResource(resource);
        refreshItem.setClientId("clientId");
        refreshItem.setAccessToken(token);
        refreshItem.setRefreshToken("refreshToken=");
        refreshItem.setExpiresOn(timeAhead.getTime());
        cache.setItem(refreshItem);
        return cache;
    }

    private void clearCache(AuthenticationContext context) {
        if (context.getCache() != null) {
            context.getCache().removeAll();
        }
    }

    class MockCache implements ITokenCacheStore {
        private static final String TAG = "MockCache";

        HashMap<String, TokenCacheItem> mCache = new HashMap<String, TokenCacheItem>();

        @Override
        public TokenCacheItem getItem(CacheKey key) {
            Log.d(TAG, "Mock cache get item:" + key.toString());
            return mCache.get(key.toString());
        }

        @Override
        public void setItem(TokenCacheItem item) {
            Log.d(TAG, "Mock cache set item:" + item.toString());
            mCache.put(CacheKey.createCacheKey(item).toString(), item);
        }

        @Override
        public void removeItem(CacheKey key) {
            // TODO Auto-generated method stub
        }

        @Override
        public void removeItem(TokenCacheItem item) {
            // TODO Auto-generated method stub
        }

        @Override
        public boolean contains(CacheKey key) {
            // TODO Auto-generated method stub
            return false;
        }

        @Override
        public void removeAll() {
            // TODO Auto-generated method stub
        }
    }

    class MockDiscovery implements IDiscovery {

        private boolean isValid = false;

        private URL authorizationUrl;

        MockDiscovery(boolean validFlag) {
            isValid = validFlag;
        }

        @Override
        public void isValidAuthority(URL authorizationEndpoint,
                AuthenticationCallback<Boolean> callback) {
            authorizationUrl = authorizationEndpoint;
            callback.onSuccess(isValid);
        }
    }

    /**
     * Mock activity
     */
    class MockActivity extends Activity {

        private static final String TAG = "MockActivity";

        int mStartActivityRequestCode = -123;

        Intent mStartActivityIntent;

        CountDownLatch mSignal;

        Bundle mStartActivityOptions;

        @Override
        public String getPackageName() {
            return ReflectionUtils.TEST_PACKAGE_NAME;
        }

        @Override
        public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
            Log.d(TAG, "startActivityForResult:" + requestCode);
            mStartActivityIntent = intent;
            mStartActivityRequestCode = requestCode;
            mStartActivityOptions = options;
            // test call needs to stop the tests at this point. If it reaches
            // here, it means authenticationActivity was attempted to launch.
            // Since it is mock activity, it will not launch something.
            if (mSignal != null)
                mSignal.countDown();
        }

    }

    class TestMockContext extends MockContext {

        private Context mContext;

        private static final String PREFIX = "test.";

        boolean resolveIntent = true;

        public TestMockContext(Context context) {
            mContext = context;
        }

        @Override
        public String getPackageName() {
            return PREFIX;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return mContext.getSharedPreferences(name, mode);
        }

        @Override
        public PackageManager getPackageManager() {
            return new TestPackageManager();
        }

        class TestPackageManager extends MockPackageManager {
            @Override
            public ResolveInfo resolveActivity(Intent intent, int flags) {
                if (resolveIntent)
                    return new ResolveInfo();

                return null;
            }
        }
    }
}