package net.kaoriya.http2client.experiment;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.api.Stream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.frames.DataFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.FuturePromise;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.http.MetaData;

public class App {
    public String getGreeting() {
        return "Hello world.";
    }

    public static void doWithHttpClient() throws Exception {
        var sc = new SslContextFactory.Client();
        var hc = new HttpClient(sc);
        hc.setFollowRedirects(false);
        hc.start();
        System.out.println("HERE_A0");
        hc.newRequest("https://www.google.co.jp/")
            .version(HttpVersion.HTTP_2)
            .send(result -> {
                System.out.println("HERE_A2: " + result.getRequest().getVersion().toString());
            });
        System.out.println("HERE_A1");
    }

    public static void main(String[] args) throws Exception {
        var startTime = System.nanoTime();

        var client = new HTTP2Client();
        var sc = new SslContextFactory.Client();
        client.addBean(sc);
        client.start();

        var host = "www.google.co.jp";
        var port = 443;

        FuturePromise<Session> sessionPromise = new FuturePromise<>();
        client.connect(sc, new InetSocketAddress(host, port), new ServerSessionListener.Adapter(), sessionPromise);

        var session = sessionPromise.get(5, TimeUnit.SECONDS);

        var requestFields = new HttpFields();
        requestFields.put("User-Agent", client.getClass().getName() + "/0.1");

        MetaData.Request request = new MetaData.Request("GET", new HttpURI("https://" + host + ":" + port + "/"), HttpVersion.HTTP_2, requestFields);
        // Create the HTTP/2 HEADERS frame representing the HTTP request.
        var headersFrame = new HeadersFrame(request, null, true);

        // Prepare the listener to receive the HTTP response frames.
        var responseListener = new Stream.Listener.Adapter()
        {
            @Override
            public void onData(Stream stream, DataFrame frame, Callback callback)
            {
                byte[] bytes = new byte[frame.getData().remaining()];
                frame.getData().get(bytes);
                int duration = (int) TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startTime);
                if (frame.isEndStream()) {
                    System.out.println("onData: end: at " + duration + " seconds");
                }
                //System.out.println("onData: stream.id=" + stream.getId() + " bytes.length=" + bytes.length + " end=" + frame.isEndStream());
                callback.succeeded();
            }

            @Override
            public void onReset(Stream stream, ResetFrame frame) {
                System.out.println("onReset");
            }
        };

        while (true) {
            session.newStream(headersFrame, new FuturePromise<>(), responseListener);
            Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        }

        //client.stop();
    }
}
