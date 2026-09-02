import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;

public class ToneGenerator {
   private static final float SAMPLE_RATE = 44100.0F;
   private static final int NOTE_MS = 150;
   private static final double[] STEP_RATIOS = new double[]{(double)1.0F, (double)1.125F, (double)1.25F, (double)1.5F};

   public ToneGenerator() {
   }

   public static Clip generateClip(String var0) throws LineUnavailableException {
      double var1 = (double)220.0F + (double)(Math.abs(var0.hashCode()) % 5) * (double)55.0F;
      short var3 = 6615;
      byte[] var4 = new byte[var3 * STEP_RATIOS.length * 2];
      int var5 = 0;

      for(double var9 : STEP_RATIOS) {
         double var11 = var1 * var9;

         for(int var13 = 0; var13 < var3; ++var13) {
            double var14 = (Math.PI * 2D) * (double)var13 * var11 / (double)44100.0F;
            double var16 = (double)1.0F - (double)var13 / (double)var3;
            short var18 = (short)((int)(Math.sin(var14) * (double)32767.0F * 0.4 * var16));
            var4[var5++] = (byte)(var18 & 255);
            var4[var5++] = (byte)(var18 >> 8 & 255);
         }
      }

      AudioFormat var20 = new AudioFormat(44100.0F, 16, 1, true, false);
      Clip var21 = AudioSystem.getClip();
      var21.open(var20, var4, 0, var4.length);
      return var21;
   }
}