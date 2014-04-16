package fergusonmodel;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import util.LinkedHashMap;

public class Host {

   public Host(int patchID, int id) {
      this.patchID = patchID;
      this.id = id;
      //Allocate this bitset once, then clear it whenever this host is reincarnated
      //todo - precalculate this
      int length = ((Main.simulationInfo.numEpitopes * Main.simulationInfo.codonsPerEpitope * 20 + 7) / 8);
      immuneHistory = new byte[length];
      Reset();
   }

   public void Reset() {
      lastInfectionDate = Integer.MIN_VALUE;
      lastInfectionStrain = null;
      for (int i = 0; i < immuneHistory.length; i++) {
         immuneHistory[i] = 0;
      }
   }

   public boolean IsIncubating(int date) {
      return lastInfectionDate != Integer.MIN_VALUE && (date - lastInfectionDate) < 2;
   }

   public boolean IsInfectious(int date) {
      return lastInfectionDate != Integer.MIN_VALUE && (date - lastInfectionDate) >= 2 && (date - lastInfectionDate) < 6;
   }

   public boolean IsSick(int date) {
      return lastInfectionDate != Integer.MIN_VALUE && (date - lastInfectionDate) < 6;
   }

   public void Infect(int date, Strain strain) {
      lastInfectionDate = date;
      lastInfectionStrain = strain;
      //Update the immune history
      for (int i = 0; i < strain.epitopes.length; i++) {
         for (int j = 0; j < strain.epitopes[0].length; j++) {
            int bitIndex = i * strain.epitopes[0].length * 20 + j * 20 + (strain.epitopes[i][j] & 0xFF);
            immuneHistory[bitIndex / 8] = (byte) ((immuneHistory[bitIndex / 8] & 0xFF) | (1 << (bitIndex % 8)));
         }
      }
   }

   public int GetImmunityDistance(Strain strain) {
      int sum = 0;
      for (int i = 0; i < strain.epitopes.length; i++) {
         for (int j = 0; j < strain.epitopes[0].length; j++) {
            int bitIndex = i * strain.epitopes[0].length * 20 + j * 20 + (strain.epitopes[i][j] & 0xFF);
            if (((immuneHistory[bitIndex / 8] & 0xFF) & (1 << (bitIndex % 8))) == 0) {
               ++sum;
            }
         }
      }
      return sum;
   }

   public void SaveState(DataOutput out) throws IOException {
      out.writeInt(lastInfectionDate);
      out.writeBoolean(lastInfectionStrain != null);
      if (lastInfectionStrain != null) {
         out.writeInt(lastInfectionStrain.stats.id);
      }
      out.write(immuneHistory);
   }

   public void LoadState(DataInput in, LinkedHashMap<Integer, Strain> strains) throws IOException {
      lastInfectionDate = in.readInt();
      if (in.readBoolean()) {
         int strainID = in.readInt();
         lastInfectionStrain = strains.Get(strainID);
      }
      in.readFully(immuneHistory);
   }

   @Override
   public int hashCode() {
      return (patchID << 24) | id;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == null) {
         return false;
      }
      if (getClass() != obj.getClass()) {
         return false;
      }
      final Host other = (Host) obj;
      if (this.patchID != other.patchID) {
         return false;
      }
      if (this.id != other.id) {
         return false;
      }
      return true;
   }
   public static int[] neighborList;
   public int patchID;
   public int id;
   public byte numNeighbors;
   public int neighborIndex;
   public int lastInfectionDate;
   public Strain lastInfectionStrain;
   public byte[] immuneHistory; //boolean[4][3][20] (sites, codons/site, possible codons)
}
