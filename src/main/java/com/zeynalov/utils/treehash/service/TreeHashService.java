package com.zeynalov.utils.treehash.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Service
public class TreeHashService {

    private static final String ALGORITHM_SHA_256 = "SHA-256";

    private static final Long ONE_MB = 1024L * 1024L;

    private static final Logger LOGGER = LoggerFactory.getLogger(TreeHashService.class);

    private static final ThreadLocal<ThreadContext> THREAD_LOCAL = new ThreadLocal<>();

    private final ThreadPoolExecutor threadExecutor;

    @Autowired
    public TreeHashService(final Executor asyncExecutor) {
        threadExecutor = ((ThreadPoolTaskExecutor) asyncExecutor).getThreadPoolExecutor();
    }

    public int getNumChunksForFile(final Path file) {
        final long filesize = file.toFile().length();
        long numChunks = filesize / ONE_MB;
        if (filesize % ONE_MB > 0) {
            numChunks++;
        }
        return (int) numChunks;
    }

    public void waiting(final String message) throws InterruptedException {
        while (!threadExecutor.getQueue().isEmpty()) {
            if (message != null) {
                System.out.print("\r" + message + ": " + threadExecutor.getQueue().size() + " job(s).");
            }
            Thread.sleep(500);
        }
        if (message != null) {
            System.out.println();
        }
    }

    @Async("asyncExecutor")
    public void getChunkSHA256HashAsync(final FileChannel fileChannel, final byte[][] hashes, final int index) throws NoSuchAlgorithmException, IOException {
        final ThreadContext ctx = getContext();

        final long position = (long) index * ONE_MB;
        ctx.buffer.clear();
        final int length = fileChannel.read(ctx.buffer, position);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Read {}:{}", position, length);
        }
        if (length == 0 && index < hashes.length-1) {
            LOGGER.warn("For some reason read length is zero, but it is not the end of the file. {}:{}", position, length);
        }
        ctx.md.reset();
        ctx.md.update(ctx.buffer.array(), 0, length);
        hashes[index] = ctx.md.digest();
    }

    @Async("asyncExecutor")
    public void computeHashAsync(final byte[] hash1, final byte[] hash2, final byte[][] hashes, final int index) throws NoSuchAlgorithmException {
        final ThreadContext ctx = getContext();

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Computing hash");
        }
        ctx.md.reset();
        ctx.md.update(hash1);
        ctx.md.update(hash2);
        hashes[index] = ctx.md.digest();
    }

    public byte[] getHash() throws NoSuchAlgorithmException {
        final ThreadContext ctx = getContext();
        ctx.md.reset();
        return ctx.md.digest();
    }

    private ThreadContext getContext() throws NoSuchAlgorithmException {
        ThreadContext context = THREAD_LOCAL.get();
        if (context == null) {
            LOGGER.debug("Initialize context");
            context = new ThreadContext(ALGORITHM_SHA_256, ONE_MB.intValue());
        }
        return context;
    }

    private static class ThreadContext {

        final MessageDigest md;

        final ByteBuffer buffer;

        ThreadContext(final String algorithm, final int bufferSize) throws NoSuchAlgorithmException {
            md = MessageDigest.getInstance(algorithm);
            buffer = ByteBuffer.allocate(bufferSize);
        }
    }

}
