import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class Mixer {
   private final Map<String, Track> tracks = new LinkedHashMap();
   private final Object lock = new Object();
   private final CountDownLatch startGate = new CountDownLatch(1);

   public Mixer() {
   }

   public CountDownLatch getStartGate() {
      return this.startGate;
   }

   public void releaseAll() {
      this.startGate.countDown();
   }

   public void addTrack(Track var1) {
      synchronized(this.lock) {
         this.tracks.put(this.key(var1.getName()), var1);
      }

      Thread var2 = new Thread(var1, "faixa-" + var1.getName());
      var2.setDaemon(true);
      var2.start();
   }

   public Track removeTrack(String var1) {
      Track var2;
      synchronized(this.lock) {
         var2 = (Track)this.tracks.remove(this.key(var1));
      }

      if (var2 != null) {
         var2.requestStop();
      }

      return var2;
   }

   public Track get(String var1) {
      synchronized(this.lock) {
         return (Track)this.tracks.get(this.key(var1));
      }
   }

   public boolean exists(String var1) {
      synchronized(this.lock) {
         return this.tracks.containsKey(this.key(var1));
      }
   }

   public List<Track> allTracks() {
      synchronized(this.lock) {
         return new ArrayList(this.tracks.values());
      }
   }

   public void stopAll() {
      synchronized(this.lock) {
         for(Track var3 : this.tracks.values()) {
            var3.requestStop();
         }

         this.tracks.clear();
      }
   }

   private String key(String var1) {
      return var1.toLowerCase(Locale.ROOT);
   }
}
