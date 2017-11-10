package com.zeynalov.utils.treehash.main;

import com.zeynalov.utils.treehash.service.TreeHashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.PropertySource;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;

import static java.nio.file.StandardOpenOption.READ;

@SpringBootApplication
@PropertySource(value = {"classpath:application.properties"})
@ComponentScan(basePackages = {"com.zeynalov.utils"})
public class Application implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private final TreeHashService treeHashService;


    private final ApplicationContext context;

    @Autowired
    public Application(final TreeHashService treeHashService, final ApplicationContext context) {
        this.treeHashService = treeHashService;
        this.context = context;
    }

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        URI uri = new URI("file:" + args.getNonOptionArgs().get(0));

        LOGGER.info("Calculating tree hash for: {}", uri.toString());
        final byte[][] hashes = getChunkSHA256Hashes(Paths.get(uri));
        final byte[] treeHash = computeSHA256TreeHash(hashes);
        LOGGER.info("Tree Hash = {}", toHex(treeHash));

        SpringApplication.exit(context, () -> 0);
    }

    private byte[][] getChunkSHA256Hashes(final Path file) throws InterruptedException, NoSuchAlgorithmException, IOException {
        final int numChunks = treeHashService.getNumChunksForFile(file);
        if (numChunks == 0) {
            return new byte[][]{treeHashService.getHash()};
        }

        final FileChannel fileChannel = FileChannel.open(file, READ);
        byte[][] hashes = new byte[(int) numChunks][];
        for (int i = 0; i < hashes.length; i++) {
            treeHashService.getChunkSHA256HashAsync(fileChannel, hashes, i);
        }
        treeHashService.waiting("Calculate chunk hashes");

        fileChannel.close();
        return hashes;
    }

    private byte[] computeSHA256TreeHash(final byte[][] hashes) throws NoSuchAlgorithmException, InterruptedException {
        byte[][] prevLvlHashes = hashes;

        while (prevLvlHashes.length > 1) {

            int len = prevLvlHashes.length / 2;
            if (prevLvlHashes.length % 2 != 0) {
                len++;
            }

            byte[][] currLvlHashes = new byte[len][];

            int j = 0;
            for (int i = 0; i < prevLvlHashes.length; i = i + 2, j++) {

                // If there are at least two elements remaining
                if (prevLvlHashes.length - i > 1) {
                    // Calculate a digest of the concatenated nodes
                    treeHashService.computeHashAsync(prevLvlHashes[i], prevLvlHashes[i + 1], currLvlHashes, j);
                } else { // Take care of remaining odd chunk
                    currLvlHashes[j] = prevLvlHashes[i];
                }
            }
            treeHashService.waiting("Calculate tree hash");

            prevLvlHashes = currLvlHashes;
        }

        return prevLvlHashes[0];
    }

    private String toHex(final byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);

        for (final byte aData : data) {
            final String hex = Integer.toHexString(aData & 0xFF);
            if (hex.length() == 1) {
                // Append leading zero.
                sb.append("0");
            }
            sb.append(hex);
        }
        return sb.toString().toLowerCase();
    }

}
