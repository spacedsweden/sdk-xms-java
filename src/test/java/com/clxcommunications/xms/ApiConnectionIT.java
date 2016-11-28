package com.clxcommunications.xms;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.theInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.eclipse.jetty.util.ConcurrentArrayQueue;
import org.junit.Rule;
import org.junit.Test;
import org.threeten.bp.Clock;
import org.threeten.bp.OffsetDateTime;
import org.threeten.bp.ZoneOffset;

import com.clxcommunications.testsupport.TestUtils;
import com.clxcommunications.xms.api.ApiError;
import com.clxcommunications.xms.api.BatchDeliveryReport;
import com.clxcommunications.xms.api.BatchId;
import com.clxcommunications.xms.api.DeliveryStatus;
import com.clxcommunications.xms.api.MtBatchBinarySmsCreate;
import com.clxcommunications.xms.api.MtBatchBinarySmsResult;
import com.clxcommunications.xms.api.MtBatchBinarySmsUpdate;
import com.clxcommunications.xms.api.MtBatchSmsResult;
import com.clxcommunications.xms.api.MtBatchTextSmsCreate;
import com.clxcommunications.xms.api.MtBatchTextSmsResult;
import com.clxcommunications.xms.api.MtBatchTextSmsUpdate;
import com.clxcommunications.xms.api.Page;
import com.clxcommunications.xms.api.PagedBatchResult;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import uk.org.lidalia.slf4jtest.TestLoggerFactoryResetRule;

public class ApiConnectionIT {

	/**
	 * A convenient {@link FutureCallback} for use in tests. By default all
	 * callback methods will call {@link #fail(String)}. Override the one that
	 * should succeed.
	 * 
	 * @param <T>
	 *            the callback result type
	 */
	private static class TestCallback<T> implements FutureCallback<T> {

		@Override
		public void failed(Exception e) {
			fail("API call unexpectedly failed with '" + e.getMessage() + "'");
		}

		@Override
		public void completed(T result) {
			fail("API call unexpectedly completed with '" + result + "'");
		}

		@Override
		public void cancelled() {
			fail("API call unexpectedly cancelled");
		}

	}

	private final ApiObjectMapper json = new ApiObjectMapper();

	@Rule
	public WireMockRule wm = new WireMockRule(
	        WireMockConfiguration.options()
	                .dynamicPort()
	                .dynamicHttpsPort());

	@Rule
	public TestLoggerFactoryResetRule testLoggerFactoryResetRule =
	        new TestLoggerFactoryResetRule();

	@Test
	public void canPostBinaryBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.now(Clock.systemUTC());

		MtBatchBinarySmsCreate request =
		        ClxApi.buildBatchBinarySms()
		                .from("12345")
		                .addTo("123456789")
		                .addTo("987654321")
		                .body("body".getBytes(TestUtils.US_ASCII))
		                .udh("udh".getBytes(TestUtils.US_ASCII))
		                .build();

		MtBatchBinarySmsResult expected =
		        new MtBatchBinarySmsResult.Builder()
		                .from(request.from())
		                .to(request.to())
		                .body(request.body())
		                .udh(request.udh())
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		String path = "/" + username + "/batches";

		stubPostResponse(expected, path, 201);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchBinarySmsResult actual = conn.sendBatch(request);
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyPostRequest(path, request);
	}

	@Test
	public void canPostTextBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		MtBatchTextSmsCreate request =
		        ClxApi.buildBatchTextSms()
		                .from("12345")
		                .addTo("123456789")
		                .addTo("987654321")
		                .body("Hello, world!")
		                .build();

		MtBatchTextSmsResult expected =
		        new MtBatchTextSmsResult.Builder()
		                .from(request.from())
		                .to(request.to())
		                .body(request.body())
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		String path = "/" + username + "/batches";

		stubPostResponse(expected, path, 201);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchTextSmsResult actual = conn.sendBatch(request);
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyPostRequest(path, request);
	}

	@Test
	public void canPostTextBatchWithSubstitutions() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		MtBatchTextSmsCreate request =
		        ClxApi.buildBatchTextSms()
		                .from("12345")
		                .addTo("123456789")
		                .addTo("987654321")
		                .body("Hello, ${name}!")
		                .putParameter("name",
		                        ClxApi.buildSubstitution()
		                                .putSubstitution("123456789", "Jane")
		                                .defaultValue("world")
		                                .build())
		                .build();

		MtBatchTextSmsResult expected =
		        new MtBatchTextSmsResult.Builder()
		                .from(request.from())
		                .to(request.to())
		                .body(request.body())
		                .parameters(request.parameters())
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		String path = "/" + username + "/batches";

		stubPostResponse(expected, path, 201);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchTextSmsResult actual = conn.sendBatch(request);
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyPostRequest(path, request);
	}

	@Test
	public void canHandleBatchPostWithError() throws Exception {
		String username = TestUtils.freshUsername();

		MtBatchTextSmsCreate request =
		        ClxApi.buildBatchTextSms()
		                .from("12345")
		                .addTo("123456789")
		                .addTo("987654321")
		                .body("Hello, world!")
		                .build();

		ApiError apiError = ApiError.of("syntax_constraint_violation",
		        "The syntax constraint was violated");

		String path = "/" + username + "/batches";

		stubPostResponse(apiError, path, 400);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			conn.sendBatch(request);
			fail("Expected exception, got none");
		} catch (ErrorResponseException e) {
			assertThat(e.getCode(), is(apiError.code()));
			assertThat(e.getText(), is(apiError.text()));
		} finally {
			conn.close();
		}
	}

	@Test
	public void canHandleBatchPostWithInvalidJson() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();

		MtBatchTextSmsCreate request =
		        ClxApi.buildBatchTextSms()
		                .from("12345")
		                .addTo("123456789")
		                .addTo("987654321")
		                .body("Hello, world!")
		                .build();

		String response = String.join("\n",
		        "{",
		        "  'to': [",
		        "    '123456789',",
		        "    '987654321'",
		        "  ],",
		        "  'body': 'Hello, world!',",
		        "  'type' 'mt_text',",
		        "  'canceled': false,",
		        "  'id': '" + batchId.id() + "',",
		        "  'from': '12345',",
		        "  'created_at': '2016-10-02T09:34:28.542Z',",
		        "  'modified_at': '2016-10-02T09:34:28.542Z'",
		        "}").replace('\'', '"');

		String path = "/" + username + "/batches";

		wm.stubFor(post(urlEqualTo(path))
		        .willReturn(aResponse()
		                .withStatus(201)
		                .withHeader("Content-Type",
		                        "application/json; charset=UTF-8")
		                .withBody(response)));

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			conn.sendBatch(request);
			fail("Expected exception, got none");
		} catch (ConcurrentException e) {
			assertThat(e.getCause(), is(instanceOf(JsonParseException.class)));
		} finally {
			conn.close();
		}
	}

	@Test
	public void canUpdateSimpleTextBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		MtBatchTextSmsUpdate request =
		        ClxApi.buildBatchTextSmsUpdate()
		                .from("12345")
		                .body("Hello, world!")
		                .unsetDeliveryReport()
		                .unsetExpireAt()
		                .build();

		MtBatchTextSmsResult expected =
		        new MtBatchTextSmsResult.Builder()
		                .from(request.from())
		                .addTo("123")
		                .body(request.body())
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		String path = "/" + username + "/batches/" + batchId.id();

		stubPostResponse(expected, path, 201);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchTextSmsResult actual =
			        conn.updateBatchAsync(batchId, request, null).get();
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyPostRequest(path, request);
	}

	@Test
	public void canUpdateSimpleBinaryBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		Set<String> tags = new TreeSet<String>();
		tags.add("tag1");
		tags.add("tag2");

		MtBatchBinarySmsUpdate request =
		        ClxApi.buildBatchBinarySmsUpdate()
		                .from("12345")
		                .body("howdy".getBytes(TestUtils.US_ASCII))
		                .unsetExpireAt()
		                .build();

		MtBatchBinarySmsResult expected =
		        new MtBatchBinarySmsResult.Builder()
		                .from(request.from())
		                .addTo("123")
		                .body(request.body())
		                .udh((byte) 1, (byte) 0xff)
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		String path = "/" + username + "/batches/" + batchId.id();

		stubPostResponse(expected, path, 201);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("toktok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchBinarySmsResult actual =
			        conn.updateBatchAsync(batchId, request, null).get();
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyPostRequest(path, request);
	}

	@Test
	public void canFetchTextBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		String path = "/" + username + "/batches/" + batchId.id();

		final MtBatchSmsResult expected =
		        new MtBatchTextSmsResult.Builder()
		                .from("12345")
		                .addTo("123456789", "987654321")
		                .body("Hello, world!")
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		stubGetResponse(expected, path);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchSmsResult actual = conn.fetchBatch(batchId);
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	@Test
	public void canFetchTextBatchAsync() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		String path = "/" + username + "/batches/" + batchId.id();

		final MtBatchSmsResult expected =
		        new MtBatchTextSmsResult.Builder()
		                .from("12345")
		                .addTo("123456789", "987654321")
		                .body("Hello, world!")
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		stubGetResponse(expected, path);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			FutureCallback<MtBatchSmsResult> testCallback =
			        new TestCallback<MtBatchSmsResult>() {

				        @Override
				        public void completed(MtBatchSmsResult result) {
					        assertThat(result, is(expected));
				        }

			        };

			MtBatchSmsResult actual =
			        conn.fetchBatchAsync(batchId, testCallback).get();
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	@Test
	public void canFetchBinaryBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);

		String path = "/" + username + "/batches/" + batchId.id();

		final MtBatchSmsResult expected =
		        new MtBatchBinarySmsResult.Builder()
		                .from("12345")
		                .addTo("123456789", "987654321")
		                .body((byte) 0xf0, (byte) 0x0f)
		                .udh((byte) 0x50, (byte) 0x05)
		                .canceled(false)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		stubGetResponse(expected, path);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			FutureCallback<MtBatchSmsResult> testCallback =
			        new TestCallback<MtBatchSmsResult>() {

				        @Override
				        public void completed(MtBatchSmsResult result) {
					        assertThat(result, is(expected));
				        }

			        };

			MtBatchSmsResult actual =
			        conn.fetchBatchAsync(batchId, testCallback).get();
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	@Test
	public void canHandle404WhenFetchingBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();

		String path = "/" + username + "/batches/" + batchId.id();

		wm.stubFor(get(
		        urlEqualTo(path))
		                .willReturn(aResponse()
		                        .withStatus(404)
		                        .withHeader("Content-Type",
		                                ContentType.TEXT_PLAIN.toString())
		                        .withBody("BAD")));

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		/*
		 * The exception we'll receive in the callback. Need to store it to
		 * verify that it is the same exception as received from #get().
		 */
		final AtomicReference<Exception> failException =
		        new AtomicReference<Exception>();

		try {
			/*
			 * Used to make sure callback and test thread are agreeing about the
			 * failException variable.
			 */
			final CountDownLatch latch = new CountDownLatch(1);

			FutureCallback<MtBatchSmsResult> testCallback =
			        new TestCallback<MtBatchSmsResult>() {

				        @Override
				        public void failed(Exception exception) {
					        if (!failException.compareAndSet(null,
					                exception)) {
						        fail("failed called multiple times");
					        }

					        latch.countDown();
				        }

			        };

			Future<MtBatchSmsResult> future =
			        conn.fetchBatchAsync(batchId, testCallback);

			// Give plenty of time for the callback to be called.
			latch.await();

			future.get();
			fail("unexpected future get success");
		} catch (ExecutionException executionException) {
			/*
			 * The exception cause should be the same as we received in the
			 * callback.
			 */
			assertThat(failException.get(),
			        is(theInstance(executionException.getCause())));

			assertThat(executionException.getCause(),
			        is(instanceOf(UnexpectedResponseException.class)));

			UnexpectedResponseException ure =
			        (UnexpectedResponseException) executionException.getCause();

			assertThat(ure.getResponse(), notNullValue());

			assertThat(ure.getResponse().getStatusLine()
			        .getStatusCode(), is(404));

			assertThat(
			        ure.getResponse().getEntity().getContentType().getValue(),
			        is(ContentType.TEXT_PLAIN.toString()));

			byte[] buf = new byte[100];
			int read;

			InputStream contentStream = null;
			try {
				contentStream = ure.getResponse().getEntity().getContent();
				read = contentStream.read(buf);
			} catch (IOException ioe) {
				throw new AssertionError(
				        "unexpected exception: "
				                + ioe.getMessage(),
				        ioe);
			} finally {
				if (contentStream != null) {
					try {
						contentStream.close();
					} catch (IOException ioe) {
						throw new AssertionError(
						        "unexpected exception: " + ioe.getMessage(),
						        ioe);
					}
				}
			}

			assertThat(read, is(3));
			assertThat(Arrays.copyOf(buf, 3),
			        is(new byte[] { 'B', 'A', 'D' }));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	@Test
	public void canCancelBatch() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();
		OffsetDateTime time = OffsetDateTime.of(2016, 10, 2, 9, 34, 28,
		        542000000, ZoneOffset.UTC);
		String path = "/" + username + "/batches/" + batchId.id();

		MtBatchSmsResult expected =
		        new MtBatchTextSmsResult.Builder()
		                .from("12345")
		                .addTo("123456789", "987654321")
		                .body("Hello, world!")
		                .canceled(true)
		                .id(batchId)
		                .createdAt(time)
		                .modifiedAt(time)
		                .build();

		String response = json.writeValueAsString(expected);

		wm.stubFor(delete(
		        urlEqualTo(path))
		                .willReturn(aResponse()
		                        .withStatus(200)
		                        .withHeader("Content-Type",
		                                "application/json; charset=UTF-8")
		                        .withBody(response)));

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			MtBatchSmsResult result = conn.cancelBatch(batchId);
			assertThat(result, is(expected));
		} finally {
			conn.close();
		}

		wm.verify(deleteRequestedFor(
		        urlEqualTo(path))
		                .withHeader("Accept",
		                        equalTo("application/json; charset=UTF-8"))
		                .withHeader("Authorization", equalTo("Bearer tok")));
	}

	/**
	 * Verifies that the default HTTP client actually can handle multiple
	 * simultaneous requests.
	 * 
	 * @throws Exception
	 *             shouldn't happen
	 */
	@Test
	public void canCancelBatchConcurrently() throws Exception {
		String username = TestUtils.freshUsername();

		// Set up the first request (the one that will be delayed).
		MtBatchSmsResult expected1 =
		        new MtBatchTextSmsResult.Builder()
		                .from("12345")
		                .addTo("123456789", "987654321")
		                .body("Hello, world!")
		                .canceled(true)
		                .id(TestUtils.freshBatchId())
		                .createdAt(OffsetDateTime.now())
		                .modifiedAt(OffsetDateTime.now())
		                .build();

		String path1 =
		        "/" + username + "/batches/" + expected1.id().id();
		byte[] response1 = json.writeValueAsBytes(expected1);

		wm.stubFor(delete(
		        urlEqualTo(path1))
		                .willReturn(aResponse()
		                        .withFixedDelay(500) // Delay for a while.
		                        .withStatus(200)
		                        .withHeader("Content-Type",
		                                "application/json; charset=UTF-8")
		                        .withBody(response1)));

		// Set up the second request.
		MtBatchSmsResult expected2 =
		        new MtBatchBinarySmsResult.Builder()
		                .from("12345")
		                .addTo("123456789", "987654321")
		                .body("Hello, world!".getBytes())
		                .udh((byte) 1)
		                .canceled(true)
		                .id(TestUtils.freshBatchId())
		                .createdAt(OffsetDateTime.now())
		                .modifiedAt(OffsetDateTime.now())
		                .build();

		String path2 =
		        "/" + username + "/batches/" + expected2.id().id();
		byte[] response2 = json.writeValueAsBytes(expected2);

		wm.stubFor(delete(
		        urlEqualTo(path2))
		                .willReturn(aResponse()
		                        .withStatus(200)
		                        .withHeader("Content-Type",
		                                "application/json; charset=UTF-8")
		                        .withBody(response2)));

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			final Queue<MtBatchSmsResult> results =
			        new ConcurrentArrayQueue<MtBatchSmsResult>();
			final CountDownLatch latch = new CountDownLatch(2);

			FutureCallback<MtBatchSmsResult> callback =
			        new TestCallback<MtBatchSmsResult>() {

				        @Override
				        public void completed(MtBatchSmsResult result) {
					        results.add(result);
					        latch.countDown();
				        }

			        };

			conn.cancelBatchAsync(expected1.id(), callback);
			Thread.sleep(100);
			conn.cancelBatchAsync(expected2.id(), callback);

			// Wait for callback to be called.
			latch.await();

			// We expect the second message to be handled first.
			assertThat(results.size(), is(2));
			assertThat(results.poll(), is(expected2));
			assertThat(results.poll(), is(expected1));
		} finally {
			conn.close();
		}

		wm.verify(deleteRequestedFor(urlEqualTo(path1)));
		wm.verify(deleteRequestedFor(urlEqualTo(path2)));
	}

	@Test
	public void canListBatchesWithEmpty() throws Exception {
		String username = TestUtils.freshUsername();
		String path = "/" + username + "/batches?page=0";
		BatchFilter filter = ClxApi.buildBatchFilter().build();

		final Page<MtBatchSmsResult> expected =
		        new PagedBatchResult.Builder()
		                .page(0)
		                .size(0)
		                .numPages(0)
		                .build();

		stubGetResponse(expected, path);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			FutureCallback<Page<MtBatchSmsResult>> testCallback =
			        new TestCallback<Page<MtBatchSmsResult>>() {

				        @Override
				        public void completed(Page<MtBatchSmsResult> result) {
					        assertThat(result, is(expected));
				        }

			        };

			PagedFetcher<MtBatchSmsResult> fetcher = conn.fetchBatches(filter);

			Page<MtBatchSmsResult> actual =
			        fetcher.fetchAsync(0, testCallback).get();
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	@Test
	public void canListBatchesWithTwoPages() throws Exception {
		String username = TestUtils.freshUsername();
		BatchFilter filter = ClxApi.buildBatchFilter().build();

		// Prepare first page.
		String path1 = "/" + username + "/batches?page=0";

		final Page<MtBatchSmsResult> expected1 =
		        new PagedBatchResult.Builder()
		                .page(0)
		                .size(0)
		                .numPages(2)
		                .build();

		stubGetResponse(expected1, path1);

		// Prepare second page.
		String path2 = "/" + username + "/batches?page=1";

		final Page<MtBatchSmsResult> expected2 =
		        new PagedBatchResult.Builder()
		                .page(1)
		                .size(0)
		                .numPages(2)
		                .build();

		stubGetResponse(expected2, path2);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			FutureCallback<Page<MtBatchSmsResult>> testCallback =
			        new TestCallback<Page<MtBatchSmsResult>>() {

				        @Override
				        public void completed(Page<MtBatchSmsResult> result) {
					        switch (result.page()) {
					        case 0:
						        assertThat(result, is(expected1));
						        break;
					        case 1:
						        assertThat(result, is(expected2));
						        break;
					        default:
						        fail("unexpected page: " + result);
					        }
				        }

			        };

			PagedFetcher<MtBatchSmsResult> fetcher = conn.fetchBatches(filter);

			Page<MtBatchSmsResult> actual1 =
			        fetcher.fetchAsync(0, testCallback).get();
			assertThat(actual1, is(expected1));

			Page<MtBatchSmsResult> actual2 =
			        fetcher.fetchAsync(1, testCallback).get();
			assertThat(actual2, is(expected2));
		} finally {
			conn.close();
		}

		verifyGetRequest(path1);
		verifyGetRequest(path2);
	}

	@Test
	public void canIterateOverPages() throws Exception {
		String username = TestUtils.freshUsername();
		BatchFilter filter = ClxApi.buildBatchFilter().build();

		// Prepare first page.
		String path1 = "/" + username + "/batches?page=0";

		final Page<MtBatchSmsResult> expected1 =
		        new PagedBatchResult.Builder()
		                .page(0)
		                .size(1)
		                .numPages(2)
		                .addContent(
		                        new MtBatchTextSmsResult.Builder()
		                                .id(TestUtils.freshBatchId())
		                                .body("body")
		                                .canceled(false)
		                                .build())
		                .build();

		stubGetResponse(expected1, path1);

		// Prepare second page.
		String path2 = "/" + username + "/batches?page=1";

		final Page<MtBatchSmsResult> expected2 =
		        new PagedBatchResult.Builder()
		                .page(1)
		                .size(2)
		                .numPages(2)
		                .addContent(
		                        new MtBatchBinarySmsResult.Builder()
		                                .id(TestUtils.freshBatchId())
		                                .body((byte) 0)
		                                .udh((byte) 1)
		                                .canceled(false)
		                                .build())
		                .addContent(
		                        new MtBatchTextSmsResult.Builder()
		                                .id(TestUtils.freshBatchId())
		                                .body("body")
		                                .canceled(false)
		                                .build())
		                .build();

		stubGetResponse(expected2, path2);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			PagedFetcher<MtBatchSmsResult> fetcher = conn.fetchBatches(filter);

			List<Page<MtBatchSmsResult>> actuals =
			        new ArrayList<Page<MtBatchSmsResult>>();

			for (Page<MtBatchSmsResult> result : fetcher.pages()) {
				actuals.add(result);
			}

			List<Page<MtBatchSmsResult>> expecteds =
			        new ArrayList<Page<MtBatchSmsResult>>();
			expecteds.add(expected1);
			expecteds.add(expected2);

			assertThat(actuals, is(expecteds));
		} finally {
			conn.close();
		}

		verifyGetRequest(path1);
		verifyGetRequest(path2);
	}

	@Test
	public void canIterateOverBatchesWithTwoPages() throws Exception {
		String username = TestUtils.freshUsername();
		BatchFilter filter = ClxApi.buildBatchFilter().build();

		// Prepare first page.
		String path1 = "/" + username + "/batches?page=0";

		final Page<MtBatchSmsResult> expected1 =
		        new PagedBatchResult.Builder()
		                .page(0)
		                .size(1)
		                .numPages(2)
		                .addContent(
		                        new MtBatchTextSmsResult.Builder()
		                                .id(TestUtils.freshBatchId())
		                                .body("body")
		                                .canceled(false)
		                                .build())
		                .build();

		stubGetResponse(expected1, path1);

		// Prepare second page.
		String path2 = "/" + username + "/batches?page=1";

		final Page<MtBatchSmsResult> expected2 =
		        new PagedBatchResult.Builder()
		                .page(1)
		                .size(2)
		                .numPages(2)
		                .addContent(
		                        new MtBatchBinarySmsResult.Builder()
		                                .id(TestUtils.freshBatchId())
		                                .body((byte) 0)
		                                .udh((byte) 1)
		                                .canceled(false)
		                                .build())
		                .addContent(
		                        new MtBatchTextSmsResult.Builder()
		                                .id(TestUtils.freshBatchId())
		                                .body("body")
		                                .canceled(false)
		                                .build())
		                .build();

		stubGetResponse(expected2, path2);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		try {
			PagedFetcher<MtBatchSmsResult> fetcher = conn.fetchBatches(filter);

			List<MtBatchSmsResult> actuals =
			        new ArrayList<MtBatchSmsResult>();

			for (MtBatchSmsResult result : fetcher.elements()) {
				actuals.add(result);
			}

			List<MtBatchSmsResult> expecteds =
			        new ArrayList<MtBatchSmsResult>();
			expecteds.addAll(expected1.content());
			expecteds.addAll(expected2.content());

			assertThat(actuals, is(expecteds));
		} finally {
			conn.close();
		}

		verifyGetRequest(path1);
		verifyGetRequest(path2);
	}

	@Test
	public void canFetchDeliveryReportSync() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();

		String path = "/" + username + "/batches/" + batchId.id()
		        + "/delivery_report"
		        + "?type=full&status=Aborted%2CDelivered&code=200%2C300";

		final BatchDeliveryReport expected =
		        new BatchDeliveryReport.Builder()
		                .batchId(batchId)
		                .totalMessageCount(1010)
		                .addStatus(
		                        new BatchDeliveryReport.Status.Builder()
		                                .code(200)
		                                .status(DeliveryStatus.ABORTED)
		                                .count(10)
		                                .addRecipient("rec1", "rec2")
		                                .build())
		                .addStatus(
		                        new BatchDeliveryReport.Status.Builder()
		                                .code(300)
		                                .status(DeliveryStatus.DELIVERED)
		                                .count(20)
		                                .addRecipient("rec3", "rec4", "rec5")
		                                .build())
		                .build();

		stubGetResponse(expected, path);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		BatchDeliveryReportParams filter =
		        new BatchDeliveryReportParams.Builder()
		                .fullReport()
		                .addStatus(DeliveryStatus.ABORTED,
		                        DeliveryStatus.DELIVERED)
		                .addCode(200, 300)
		                .build();

		try {
			BatchDeliveryReport actual =
			        conn.fetchDeliveryReport(batchId, filter);
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	@Test
	public void canFetchDeliveryReportAsync() throws Exception {
		String username = TestUtils.freshUsername();
		BatchId batchId = TestUtils.freshBatchId();

		String path = "/" + username + "/batches/" + batchId.id()
		        + "/delivery_report?type=full";

		final BatchDeliveryReport expected =
		        new BatchDeliveryReport.Builder()
		                .batchId(batchId)
		                .totalMessageCount(1010)
		                .addStatus(
		                        new BatchDeliveryReport.Status.Builder()
		                                .code(200)
		                                .status(DeliveryStatus.ABORTED)
		                                .count(10)
		                                .addRecipient("rec1", "rec2")
		                                .build())
		                .addStatus(
		                        new BatchDeliveryReport.Status.Builder()
		                                .code(300)
		                                .status(DeliveryStatus.DELIVERED)
		                                .count(20)
		                                .addRecipient("rec3", "rec4", "rec5")
		                                .build())
		                .build();

		stubGetResponse(expected, path);

		ApiConnection conn = ApiConnection.builder()
		        .username(username)
		        .token("tok")
		        .endpoint("http://localhost:" + wm.port())
		        .start();

		BatchDeliveryReportParams filter =
		        new BatchDeliveryReportParams.Builder()
		                .fullReport()
		                .build();

		try {
			FutureCallback<BatchDeliveryReport> testCallback =
			        new TestCallback<BatchDeliveryReport>() {

				        @Override
				        public void completed(BatchDeliveryReport result) {
					        assertThat(result, is(expected));
				        }

			        };

			BatchDeliveryReport actual = conn.fetchDeliveryReportAsync(
			        batchId, filter, testCallback).get();
			assertThat(actual, is(expected));
		} finally {
			conn.close();
		}

		verifyGetRequest(path);
	}

	/**
	 * Helper that sets up WireMock to respond to a GET using a JSON body.
	 * 
	 * @param response
	 *            the response to give, serialized to JSON
	 * @param path
	 *            the path on which to listen
	 * @param status
	 *            the response HTTP status
	 * @throws JsonProcessingException
	 *             if the given response object could not be serialized
	 */
	private void stubGetResponse(Object response, String path)
	        throws JsonProcessingException {
		byte[] body = json.writeValueAsBytes(response);

		wm.stubFor(get(
		        urlEqualTo(path))
		                .willReturn(aResponse()
		                        .withStatus(200)
		                        .withHeader("Content-Type",
		                                "application/json; charset=UTF-8")
		                        .withBody(body)));
	}

	/**
	 * Helper that sets up WireMock to verify a GET request.
	 * 
	 * @param path
	 *            the request path to match
	 */
	private void verifyGetRequest(String path) {
		wm.verify(getRequestedFor(
		        urlEqualTo(path))
		                .withHeader("Accept",
		                        equalTo("application/json; charset=UTF-8"))
		                .withHeader("Authorization", equalTo("Bearer tok")));
	}

	/**
	 * Helper that sets up WireMock to respond to a POST using a JSON body.
	 * 
	 * @param response
	 *            the response to give, serialized to JSON
	 * @param path
	 *            the path on which to listen
	 * @param status
	 *            the response HTTP status
	 * @throws JsonProcessingException
	 *             if the given response object could not be serialized
	 */
	private void stubPostResponse(Object response, String path, int status)
	        throws JsonProcessingException {
		byte[] body = json.writeValueAsBytes(response);

		wm.stubFor(post(urlEqualTo(path))
		        .willReturn(aResponse()
		                .withStatus(status)
		                .withHeader("Content-Type",
		                        "application/json; charset=UTF-8")
		                .withBody(body)));
	}

	/**
	 * Helper that sets up WireMock to verify that a request matches a given
	 * object in JSON format.
	 * 
	 * @param path
	 *            the request path to match
	 * @param request
	 *            the request object whose JSON serialization should match
	 * @throws JsonProcessingException
	 *             if the given request object could not be serialized
	 */
	private void verifyPostRequest(String path, Object request)
	        throws JsonProcessingException {
		String expectedRequest = json.writeValueAsString(request);

		wm.verify(postRequestedFor(
		        urlEqualTo(path))
		                .withRequestBody(equalToJson(expectedRequest))
		                .withHeader("Content-Type",
		                        matching("application/json; charset=UTF-8"))
		                .withHeader("Accept",
		                        equalTo("application/json; charset=UTF-8"))
		                .withHeader("Authorization", equalTo("Bearer toktok")));
	}

}
