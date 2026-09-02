import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Locale;
import javax.sound.sampled.Clip;

public class Main {
   public Main() {
   }

   public static void main(String[] var0) throws Exception {
      Mixer var1 = new Mixer();
      System.out.println("Carregando faixas de stems/ ...");
      loadStem(var1, "bateria", "stems/drums.wav");
      loadStem(var1, "baixo", "stems/bass.wav");
      loadStem(var1, "synth", "stems/other.wav");
      var1.releaseAll();
      Console var2 = new Console(var1);
      var2.init();
      StatusPanel var3 = new StatusPanel(var2);
      Thread var4 = new Thread(var3, "painel-status");
      var4.setDaemon(true);
      var4.start();
      BufferedReader var5 = new BufferedReader(new InputStreamReader(System.in));

      String var6;
      while((var6 = var5.readLine()) != null) {
         var6 = var6.trim();
         if (!var6.isEmpty()) {
            String[] var7 = var6.split("\\s+");
            String var8 = var7[0].toLowerCase(Locale.ROOT);
            if (var8.equals("sair") || var8.equals("exit") || var8.equals("quit")) {
               var1.stopAll();
               var3.stopPanel();
               var2.shutdownMessage("Encerrando a mesa de DJ...");
               return;
            }

            String var9 = handleCommand(var1, var8, var7);
            var2.setMessage(var9);
            var2.promptAgain();
         } else {
            var2.promptAgain();
         }
      }

      var1.stopAll();
      var3.stopPanel();
   }

   private static String handleCommand(Mixer var0, String var1, String[] var2) {
      switch (var1) {
         case "play":
         case "resume":
            return handleResume(var0, var2);
         case "pause":
            return handlePause(var0, var2);
         case "add":
            return handleAdd(var0, var2);
         case "remove":
         case "rm":
            return handleRemove(var0, var2);
         case "list":
         case "status":
            return "Status atual de todas as faixas exibido no painel acima.";
         case "help":
            return "Comandos: play/pause/remove <faixa>, add <arquivo>, list, exit.";
         default:
            return "Comando desconhecido: " + var1 + " (digite 'help')";
      }
   }

   private static void loadStem(Mixer var0, String var1, String var2) {
      try {
         Track var3 = Track.fromFile(var1, var2, var0.getStartGate());
         var0.addTrack(var3);
         System.out.println("Faixa carregada: " + var1 + " (" + var2 + ")");
      } catch (Exception var4) {
         System.out.println("Falha ao carregar " + var1 + " de " + var2 + ": " + var4.getMessage());
      }

   }

   private static String handlePause(Mixer var0, String[] var1) {
      if (var1.length < 2) {
         return "Uso: pause <faixa>";
      } else {
         Track var2 = var0.get(var1[1]);
         if (var2 == null) {
            return "Faixa nao encontrada: " + var1[1];
         } else {
            var2.pause();
            return "Pausado: " + var2.getName();
         }
      }
   }

   private static String handleResume(Mixer var0, String[] var1) {
      if (var1.length < 2) {
         return "Uso: play <faixa>";
      } else {
         Track var2 = var0.get(var1[1]);
         if (var2 == null) {
            return "Faixa nao encontrada: " + var1[1];
         } else {
            var2.resume();
            return "Tocando: " + var2.getName();
         }
      }
   }

   private static String handleRemove(Mixer var0, String[] var1) {
      if (var1.length < 2) {
         return "Uso: remove <faixa>";
      } else {
         Track var2 = var0.removeTrack(var1[1]);
         return var2 == null ? "Faixa nao encontrada: " + var1[1] : "Faixa removida da mesa: " + var2.getName();
      }
   }

   private static String handleAdd(Mixer var0, String[] var1) {
      if (var1.length < 2) {
         return "Uso: add <arquivo> | add <nome> <arquivo> | add <nome>";
      } else {
         String var2;
         String var3;
         if (var1.length >= 3) {
            var2 = var1[1];
            var3 = var1[2];
         } else if ((new File(var1[1])).isFile()) {
            var3 = var1[1];
            var2 = nameFromPath(var3);
         } else {
            var2 = var1[1];
            var3 = null;
         }

         if (var0.exists(var2)) {
            return "Ja existe uma faixa chamada: " + var2;
         } else {
            try {
               Track var4;
               if (var3 != null) {
                  var4 = Track.fromFile(var2, var3, var0.getStartGate());
               } else {
                  Clip var5 = ToneGenerator.generateClip(var2);
                  var4 = new Track(var2, var5, var0.getStartGate(), "som sintetizado");
               }

               var0.addTrack(var4);
               return "Instrumento adicionado e tocando: " + var2;
            } catch (Exception var6) {
               return "Falha ao adicionar " + var2 + ": " + var6.getMessage();
            }
         }
      }
   }

   private static String nameFromPath(String var0) {
      String var1 = (new File(var0)).getName();
      int var2 = var1.lastIndexOf(46);
      if (var2 > 0) {
         var1 = var1.substring(0, var2);
      }

      return var1.toLowerCase(Locale.ROOT);
   }
}