package util;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Random;

//http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/MT2002/emt19937ar.html
public class MersenneTwister extends Random {

   //MT constructors
   public MersenneTwister() {
      this(System.currentTimeMillis());
   }

   public MersenneTwister(long seed) {
      init_genrand(seed);
   }

   public MersenneTwister(int[] seed) {
      init_by_array(seed, seed.length);
   }

   //MT functions
   public void init_genrand(long s) {
      mt = new long[N];
      mt[0] = s & 0xffffffffl;
      for (mti = 1; mti < N; mti++) {
         mt[mti] = (1812433253l * (mt[mti - 1] ^ (mt[mti - 1] >> 30)) + (long) mti);
         mt[mti] &= 0xffffffffl;
      }
   }

   public void init_by_array(int init_key[], int key_length) {
      int i, j, k;
      init_genrand(19650218l);
      i = 1;
      j = 0;
      k = (N > key_length ? N : key_length);
      for (; k != 0; k--) {
         mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >> 30)) * 1664525l)) + (init_key[j] & 0xffffffffl) + j;
         mt[i] &= 0xffffffffl;
         i++;
         j++;
         if (i >= N) {
            mt[0] = mt[N - 1];
            i = 1;
         }
         if (j >= key_length) {
            j = 0;
         }
      }
      for (k = N - 1; k != 0; k--) {
         mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >> 30)) * 1566083941l)) - i;
         mt[i] &= 0xffffffffl;
         i++;
         if (i >= N) {
            mt[0] = mt[N - 1];
            i = 1;
         }
      }
      mt[0] = 0x80000000l;
   }

   public long genrand_int32() {
      long y;
      long[] mag01 = {0x0, MATRIX_A};
      if (mti >= N) {
         int kk;
         if (mti == N + 1) {
            init_genrand(5489l);
         }
         for (kk = 0; kk < N - M; kk++) {
            y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
            mt[kk] = mt[kk + M] ^ (y >> 1) ^ mag01[(int) (y & 0x1)];
         }
         for (; kk < N - 1; kk++) {
            y = (mt[kk] & UPPER_MASK) | (mt[kk + 1] & LOWER_MASK);
            mt[kk] = mt[kk + (M - N)] ^ (y >> 1) ^ mag01[(int) (y & 0x1)];
         }
         y = (mt[N - 1] & UPPER_MASK) | (mt[0] & LOWER_MASK);
         mt[N - 1] = mt[M - 1] ^ (y >> 1) ^ mag01[(int) (y & 0x1)];
         mti = 0;
      }
      y = mt[mti++];
      y ^= (y >> 11);
      y ^= (y << 7) & 0x9d2c5680l;
      y ^= (y << 15) & 0xefc60000l;
      y ^= (y >> 18);
      return y & 0xffffffffl;
   }

   public long genrand_int31() {
      return genrand_int32() >>> 1;
   }

   public double genrand_real1() {
      return genrand_int32() * (1.0 / 4294967295.0);
   }

   public double genrand_real2() {
      return genrand_int32() * (1.0 / 4294967296.0);
   }

   public double genrand_real3() {
      return (((double) genrand_int32()) + 0.5) * (1.0 / 4294967296.0);
   }

   public double genrand_res53() {
      long a = genrand_int32() >>> 5, b = genrand_int32() >>> 6;
      return (a * 67108864.0 + b) * (1.0 / 9007199254740992.0);
   }

   public static int PrintTestVectors() {
      MersenneTwister mt = new MersenneTwister(new int[]{0x123, 0x234, 0x345, 0x456});
      System.out.print(String.format("1000 outputs of genrand_int32()\n"));
      for (int i = 0; i < 1000; i++) {
         System.out.print(String.format("%10d ", mt.genrand_int32()));
         if (i % 5 == 4) {
            System.out.print(String.format("\n"));
         }
      }
      System.out.print(String.format("\n1000 outputs of genrand_real2()\n"));
      for (int i = 0; i < 1000; i++) {
         System.out.print(String.format("%10.8f ", mt.genrand_real2()));
         if (i % 5 == 4) {
            System.out.print(String.format("\n"));
         }
      }
      return 0;
   }

   //java.util.Random functions
   @Override
   protected int next(int bits) {
      return (int) (genrand_int32() >>> (32 - bits));
   }

   @Override
   public boolean nextBoolean() {
      return genrand_int32() % 2L == 0;
   }

   @Override
   public void nextBytes(byte[] bytes) {
      super.nextBytes(bytes);
   }

   @Override
   public double nextDouble() {
      return genrand_real2();
   }

   @Override
   public float nextFloat() {
      return (float) genrand_real2();
   }

   @Override
   public synchronized double nextGaussian() {
      return super.nextGaussian();
   }

   @Override
   public int nextInt() {
      return (int) genrand_int32();
   }

   @Override
   public int nextInt(int n) {
      return (int) (genrand_int32() % (long) n);
   }

   @Override
   public long nextLong() {
      return (genrand_int32() << 32) | genrand_int32();
   }

   @Override
   public synchronized void setSeed(long seed) {
      init_genrand(seed);
   }

   //Custom functions
   public void SaveState(DataOutput out) throws IOException {
      for (int i = 0; i < mt.length; i++) {
         out.writeLong(mt[i]);
      }
      out.writeInt(mti);
   }

   public void LoadState(DataInput in) throws IOException {
      for (int i = 0; i < mt.length; i++) {
         mt[i] = in.readLong();
      }
      mti = in.readInt();
   }
   //MT fields
   protected static final int N = 624;
   protected static final int M = 397;
   protected static final long MATRIX_A = 0x9908b0dfl;
   protected static final long UPPER_MASK = 0x80000000l;
   protected static final long LOWER_MASK = 0x7fffffffl;
   protected long[] mt;
   protected int mti = N + 1;
}
