// package de.jeha.s3pt.utils;

// import org.junit.Test;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import static org.junit.Assert.assertEquals;

// /**
//  * @author jenshadlich@googlemail.com
//  */
// public class RandomGeneratedInputStreamTest {

//     private static final Logger LOG = LoggerFactory.getLogger(RandomGeneratedInputStreamTest.class);


//     @Test
//     public void testData() throws Exception{
//         RandomGeneratedInputStream is = new RandomGeneratedInputStream(8000,4096);
//         byte[] buffer = new byte[8000];

//         for (int i = 0; i < 8; i++) {
//             //LOG.info("Reading {}, {}",i * 1000, 1000);
//             int resp = is.read(buffer, 1000*i, 1000);
//             assertEquals(resp, 1000);
//         }
//         int read = is.read();
//         assertEquals(read, -1);
//     }

//     @Test
//     public void testMarking() throws Exception{
//         RandomGeneratedInputStream is = new RandomGeneratedInputStream(8000,4096);
//         byte[] buffer = new byte[8000];
//         byte[] buffer2 = new byte[8000];
//         is.mark(4096);
//         for (int i = 0; i < 8; i++) {
//             //LOG.info("Reading {}, {}",i * 1000, 1000);
//             int resp = is.read(buffer, 1000*i, 1000);
//             assertEquals(resp, 1000);
//             is.reset();
//             int resp2 = is.read(buffer2, 1000*i, 1000);
//             assertEquals(resp2, 1000);
//             is.mark(4096);
//         }
//         for (int i = 0; i < buffer.length; i++) {
//             //LOG.info("EQ: {} = {}", buffer[i], buffer2[i]);
//             assertEquals(buffer[i], buffer2[i]);
//         }
//     }


// }
