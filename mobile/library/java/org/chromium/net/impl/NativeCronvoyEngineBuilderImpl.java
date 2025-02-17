package org.chromium.net.impl;

import static io.envoyproxy.envoymobile.engine.EnvoyConfiguration.TrustChainVerification.VERIFY_TRUST_CHAIN;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import com.google.protobuf.Struct;
import io.envoyproxy.envoymobile.engine.AndroidEngineImpl;
import io.envoyproxy.envoymobile.engine.AndroidJniLibrary;
import io.envoyproxy.envoymobile.engine.AndroidNetworkMonitor;
import io.envoyproxy.envoymobile.engine.EnvoyConfiguration;
import io.envoyproxy.envoymobile.engine.EnvoyConfiguration.TrustChainVerification;
import io.envoyproxy.envoymobile.engine.EnvoyEngine;
import io.envoyproxy.envoymobile.engine.EnvoyNativeFilterConfig;
import io.envoyproxy.envoymobile.engine.types.EnvoyEventTracker;
import io.envoyproxy.envoymobile.engine.types.EnvoyHTTPFilterFactory;
import io.envoyproxy.envoymobile.engine.types.EnvoyLogger;
import io.envoyproxy.envoymobile.engine.types.EnvoyOnEngineRunning;
import io.envoyproxy.envoymobile.engine.types.EnvoyStringAccessor;
import io.envoyproxy.envoymobile.engine.types.EnvoyKeyValueStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.chromium.net.ExperimentalCronetEngine;
import org.chromium.net.ICronetEngineBuilder;

/**
 * Implementation of {@link ICronetEngineBuilder} that builds native Cronvoy engine.
 */
public class NativeCronvoyEngineBuilderImpl extends CronvoyEngineBuilderImpl {

  // TODO(refactor) move unshared variables into their specific methods.
  private final List<EnvoyNativeFilterConfig> nativeFilterChain = new ArrayList<>();
  private final EnvoyEventTracker mEnvoyEventTracker = null;
  private final int mConnectTimeoutSeconds = 30;
  private final int mDnsRefreshSeconds = 60;
  private final int mDnsFailureRefreshSecondsBase = 2;
  private final int mDnsFailureRefreshSecondsMax = 10;
  private int mDnsQueryTimeoutSeconds = 5;
  private int mDnsMinRefreshSeconds = 60;
  private final List<String> mDnsPreresolveHostnames = Collections.emptyList();
  private final boolean mEnableDNSCache = false;
  private final int mDnsCacheSaveIntervalSeconds = 1;
  private final List<String> mDnsFallbackNameservers = Collections.emptyList();
  private final boolean mEnableDnsFilterUnroutableFamilies = true;
  private boolean mEnableDrainPostDnsRefresh = false;
  private final boolean mEnableGzipDecompression = true;
  private final boolean mEnableSocketTag = true;
  private final boolean mEnableInterfaceBinding = false;
  private final boolean mEnableProxying = false;
  private final int mH2ConnectionKeepaliveIdleIntervalMilliseconds = 1;
  private final int mH2ConnectionKeepaliveTimeoutSeconds = 10;
  private final int mMaxConnectionsPerHost = 7;
  private final int mStreamIdleTimeoutSeconds = 15;
  private final int mPerTryIdleTimeoutSeconds = 15;
  private final String mAppVersion = "unspecified";
  private final String mAppId = "unspecified";
  private TrustChainVerification mTrustChainVerification = VERIFY_TRUST_CHAIN;
  private final boolean mEnablePlatformCertificatesValidation = true;
  private final String mNodeId = "";
  private final String mNodeRegion = "";
  private final String mNodeZone = "";
  private final String mNodeSubZone = "";
  private final Map<String, Boolean> mRuntimeGuards = new HashMap<>();

  /**
   * Builder for Native Cronet Engine. Default config enables SPDY, disables QUIC and HTTP cache.
   *
   * @param context Android {@link Context} for engine to use.
   */
  public NativeCronvoyEngineBuilderImpl(Context context) { super(context); }

  /**
   * Enable draining of the connections after a DNS refresh changes the host address mapping.
   * The default behavior is to not enable draining post DNS refresh.
   *
   * @param enable If true, enable drain post DNS refresh; otherwise, don't.
   */
  public NativeCronvoyEngineBuilderImpl setEnableDrainPostDnsRefresh(boolean enable) {
    mEnableDrainPostDnsRefresh = enable;
    return this;
  }

  /**
   * Set the DNS query timeout, in seconds, which ensures that DNS queries succeed or fail
   * within that time range. See the DnsCacheConfig.dns_query_timeout proto field for details.
   *
   * The default is 5s.
   *
   * @param timeout The DNS query timeout value, in seconds.
   */
  public NativeCronvoyEngineBuilderImpl setDnsQueryTimeoutSeconds(int timeout) {
    mDnsQueryTimeoutSeconds = timeout;
    return this;
  }

  /**
   * Set the DNS minimum refresh time, in seconds, which ensures that we wait to refresh a DNS
   * entry for at least the minimum refresh time. For example, if the DNS record TTL is 60 seconds
   * and setMinDnsRefreshSeconds(120) is invoked, then at least 120 seconds will transpire before
   * the DNS entry for a host is refreshed.
   *
   * The default is 60s.
   *
   * @param minRefreshSeconds The DNS minimum refresh time, in seconds.
   */
  public NativeCronvoyEngineBuilderImpl setMinDnsRefreshSeconds(int minRefreshSeconds) {
    mDnsMinRefreshSeconds = minRefreshSeconds;
    return this;
  }

  /**
   * Sets the boolean value for the reloadable runtime feature flag value. For example, to set the
   * Envoy runtime flag `envoy.reloadable_features.http_allow_partial_urls_in_referer` to true,
   * call `setRuntimeGuard("http_allow_partial_urls_in_referer", true)`.
   *
   * TODO(abeyad): Change the name to setRuntimeFeature here and in the C++ APIs.
   *
   * @param feature The reloadable runtime feature flag name.
   * @param value The Boolean value to set the runtime feature flag to.
   */
  public NativeCronvoyEngineBuilderImpl setRuntimeGuard(String feature, boolean value) {
    mRuntimeGuards.put(feature, value);
    return this;
  }

  /**
   * Indicates to skip the TLS certificate verification.
   *
   * @return the builder to facilitate chaining.
   */
  @VisibleForTesting
  public CronvoyEngineBuilderImpl setMockCertVerifierForTesting() {
    mTrustChainVerification = TrustChainVerification.ACCEPT_UNTRUSTED;
    return this;
  }

  /**
   * Adds url interceptors to the cronetEngine
   *
   * @return the builder to facilitate chaining.
   */
  @VisibleForTesting
  public CronvoyEngineBuilderImpl addUrlInterceptorsForTesting() {
    nativeFilterChain.add(new EnvoyNativeFilterConfig(
        "envoy.filters.http.test_read",
        "[type.googleapis.com/envoymobile.test.integration.filters.http.test_read.TestRead] {}"));
    return this;
  }

  @Override
  public ExperimentalCronetEngine build() {
    if (getUserAgent() == null) {
      setUserAgent(getDefaultUserAgent());
    }
    return new CronvoyUrlRequestContext(this);
  }

  EnvoyEngine createEngine(EnvoyOnEngineRunning onEngineRunning, EnvoyLogger envoyLogger,
                           String logLevel) {
    AndroidEngineImpl engine = new AndroidEngineImpl(getContext(), onEngineRunning, envoyLogger,
                                                     mEnvoyEventTracker, mEnableProxying);
    AndroidJniLibrary.load(getContext());
    AndroidNetworkMonitor.load(getContext(), engine);
    engine.runWithConfig(createEnvoyConfiguration(), logLevel);
    return engine;
  }

  private EnvoyConfiguration createEnvoyConfiguration() {
    List<EnvoyHTTPFilterFactory> platformFilterChain = Collections.emptyList();
    Map<String, EnvoyStringAccessor> stringAccessors = Collections.emptyMap();
    Map<String, EnvoyKeyValueStore> keyValueStores = Collections.emptyMap();

    return new EnvoyConfiguration(
        mConnectTimeoutSeconds, mDnsRefreshSeconds, mDnsFailureRefreshSecondsBase,
        mDnsFailureRefreshSecondsMax, mDnsQueryTimeoutSeconds, mDnsMinRefreshSeconds,
        mDnsPreresolveHostnames, mEnableDNSCache, mDnsCacheSaveIntervalSeconds,
        mEnableDrainPostDnsRefresh, quicEnabled(), quicConnectionOptions(),
        quicClientConnectionOptions(), quicHints(), quicCanonicalSuffixes(),
        mEnableGzipDecompression, brotliEnabled(), portMigrationEnabled(), mEnableSocketTag,
        mEnableInterfaceBinding, mH2ConnectionKeepaliveIdleIntervalMilliseconds,
        mH2ConnectionKeepaliveTimeoutSeconds, mMaxConnectionsPerHost, mStreamIdleTimeoutSeconds,
        mPerTryIdleTimeoutSeconds, mAppVersion, mAppId, mTrustChainVerification, nativeFilterChain,
        platformFilterChain, stringAccessors, keyValueStores, mRuntimeGuards,
        mEnablePlatformCertificatesValidation,
        /*rtdsResourceName=*/"", /*rtdsTimeoutSeconds=*/0, /*xdsAddress=*/"",
        /*xdsPort=*/0, /*xdsGrpcInitialMetadata=*/Collections.emptyMap(),
        /*xdsSslRootCerts=*/"", mNodeId, mNodeRegion, mNodeZone, mNodeSubZone,
        Struct.getDefaultInstance(), /*cdsResourcesLocator=*/"", /*cdsTimeoutSeconds=*/0,
        /*enableCds=*/false);
  }
}
