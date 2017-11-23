package com.sovaalexandr.maxmind.geoip2.database.download;

import org.junit.Test;
import play.api.libs.ws.StandaloneWSClient;
import play.api.libs.ws.StandaloneWSRequest;

import java.io.File;

import static org.mockito.Mockito.*;

public class DownloadRequestTest {
  @Test
  public void should_set_filter_when_file_exists() throws Exception {
    final StandaloneWSClient wsClient = mock(StandaloneWSClient.class);
    final StandaloneWSRequest request = mock(StandaloneWSRequest.class, RETURNS_SELF);
    final File testFile = mock(File.class);
    final DownloadRequest.Settings settings = new DownloadRequest.Settings("some base url");
    when(wsClient.url("some base url")).thenReturn(request);
    final RememberedHeadersFilter filter = mock(RememberedHeadersFilter.class);

    when(testFile.isFile()).thenReturn(true);
    final DownloadRequest requestProvider = new DownloadRequest(wsClient, settings, filter);
    requestProvider.apply(testFile);

    verify(request).withRequestFilter(filter);
  }

  @Test
  public void should_not_set_filter_when_file_not_exists() throws Exception {
    final StandaloneWSClient wsClient = mock(StandaloneWSClient.class);
    final StandaloneWSRequest request = mock(StandaloneWSRequest.class, RETURNS_SELF);
    final File testFile = mock(File.class);
    final DownloadRequest.Settings settings = new DownloadRequest.Settings("some base url");
    when(wsClient.url("some base url")).thenReturn(request);
    final RememberedHeadersFilter filter = mock(RememberedHeadersFilter.class);

    when(testFile.isFile()).thenReturn(false);
    final DownloadRequest requestProvider = new DownloadRequest(wsClient, settings, filter);
    requestProvider.apply(testFile);

    verify(request, never()).withRequestFilter(filter);
  }
}
