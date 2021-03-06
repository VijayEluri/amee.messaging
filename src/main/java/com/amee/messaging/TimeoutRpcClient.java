package com.amee.messaging;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.RpcClient;
import com.rabbitmq.client.ShutdownSignalException;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A subclass of RpcClient that wraps primitiveCall such that a timeout is applied.
 */
public class TimeoutRpcClient extends RpcClient {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    private int timeout = 30;

    public TimeoutRpcClient(Channel channel, String exchange, String routingKey) throws IOException {
        super(channel, exchange, routingKey);
    }

    public TimeoutRpcClient(Channel channel, String exchange, String routingKey, int timeout) throws IOException {
        super(channel, exchange, routingKey);
        setTimeout(timeout);
    }

    @Override
    public byte[] primitiveCall(final AMQP.BasicProperties props, final byte[] message)
            throws IOException, ShutdownSignalException, TimeoutException {
        byte[] result = new byte[0];
        Callable<byte[]> task = new Callable<byte[]>() {
            @Override
            public byte[] call() throws IOException, ShutdownSignalException, TimeoutException {
                return wrappedPrimitiveCall(props, message);
            }
        };
        log.debug("primitiveCall() Submitting the task.");
        Future<byte[]> future = executor.submit(task);
        try {
            result = future.get(getTimeout(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("primitiveCall() Caught TimeoutException (aborting).");
            getChannel().abort();
        } catch (InterruptedException e) {
            log.warn("primitiveCall() Caught InterruptedException (aborting): " + e.getMessage());
            getChannel().abort();
        } catch (ExecutionException e) {
            log.warn("primitiveCall() Caught ExecutionException (aborting): " + e.getMessage());
            getChannel().abort();
        } finally {
            log.debug("primitiveCall() Canceling the task via its Future.");
            future.cancel(true);
        }
        return result;
    }

    public byte[] wrappedPrimitiveCall(AMQP.BasicProperties props, byte[] message)
            throws IOException, ShutdownSignalException, TimeoutException {
        return super.primitiveCall(props, message);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
