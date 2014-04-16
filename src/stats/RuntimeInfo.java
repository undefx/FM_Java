package stats;

import fergusonmodel.Main;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetAddress;

/*
 * Runtime information (output) is stored in this class.
 */
public class RuntimeInfo {

   public RuntimeInfo(int simulatorVersion, int simulationDate, int simulationTimer, String computerName, String implementationName, long initialState, long finalState) {
      this.simulatorVersion = simulatorVersion;
      this.simulationDate = simulationDate;
      this.simulationTimer = simulationTimer;
      this.computerName = computerName;
      this.implementationName = implementationName;
      this.initialState = initialState;
      this.finalState = finalState;
   }

   public static RuntimeInfo GenerateRuntimeInfo() {
      String implementationName = "Java";
      String computerName = "unknown";
      try {
         computerName = InetAddress.getLocalHost().getHostName();
      } catch (Exception ex) {
      }
      return new RuntimeInfo(Main.VERSION, (int) (System.currentTimeMillis() / 1000L), 0, computerName, implementationName, 0, 0);
   }

   public void UpdateSimlationTimer() {
      simulationTimer = (int) (System.currentTimeMillis() / 1000L) - simulationDate;
   }

   public void SetInitialState(long initialState) {
      this.initialState = initialState;
   }

   public void SetFinalState(long finalState) {
      this.finalState = finalState;
   }

   public void Write(DataOutput output) throws IOException {
      output.writeInt(simulatorVersion);
      output.writeInt(simulationDate);
      output.writeInt(simulationTimer);
      output.writeUTF(computerName);
      output.writeUTF(implementationName);
      output.writeLong(initialState);
      output.writeLong(finalState);
   }

   public static RuntimeInfo Read(DataInput input, int majorVersion) throws IOException {
      int simulatorVersion = 0;
      if (majorVersion >= 1) {
         //simulatorVersion introduced in 1.0
         simulatorVersion = input.readInt();
      }
      int simulationDate = input.readInt();
      int simulationTimer = input.readInt();
      String computerName = input.readUTF();
      String implementationName = input.readUTF();
      long initialState = input.readLong();
      long finalState = input.readLong();
      return new RuntimeInfo(simulatorVersion, simulationDate, simulationTimer, computerName, implementationName, initialState, finalState);
   }
   //The simulator version
   public int simulatorVersion;
   //The datetime the simulation was started (seconds since epoch, January 1, 1970)
   public int simulationDate;
   //The amount of time the simulation took in seconds
   public int simulationTimer;
   //The name of the computer (to compare performance across different machines)
   public String computerName;
   //The name of the implementation (to compare performance across different implementation)
   public String implementationName;
   //The RuntimeInfo.finalState field of the simulation which was resumed
   public long initialState;
   //This is intended to be a (reasonably) unique "hash" of the entire simulation trajectory
   public long finalState;
}
