package fergusonmodel;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import stats.StrainStats;

public class Strain {

   public Strain(int date, byte[] rna) {
      epitopes = new byte[Main.simulationInfo.numEpitopes][Main.simulationInfo.codonsPerEpitope];
      firstSeenDate = date;
      this.rna = rna;
      int nucleicAcid = 0;
      for (int i = 0; i < epitopes.length; i++) {
         for (int j = 0; j < epitopes[0].length; j++) {
            int na1 = ((rna[nucleicAcid / 4] & 0xFF) >> ((nucleicAcid % 4) * 2)) & 0x03;
            ++nucleicAcid;
            int na2 = ((rna[nucleicAcid / 4] & 0xFF) >> ((nucleicAcid % 4) * 2)) & 0x03;
            ++nucleicAcid;
            int na3 = ((rna[nucleicAcid / 4] & 0xFF) >> ((nucleicAcid % 4) * 2)) & 0x03;
            ++nucleicAcid;
            epitopes[i][j] = codonToAA[GetCodon(na1, na2, na3)];
         }
      }
      stats = new StrainStats();
      stats.rna = GetRNAString();
      stats.protein = GetProtinString();
   }

   public String GetRNAString() {
      StringBuilder sb = new StringBuilder();
      for (int spot = 0; spot < epitopes.length * epitopes[0].length * 3; ++spot) {
         int arrayIndex = spot / 4;
         int shift = (spot % 4) * 2;
         int na1 = ((rna[arrayIndex] & 0xFF) >> shift) & 0x03;
         switch (na1) {
            case U:
               sb.append("U");
               break;
            case C:
               sb.append("C");
               break;
            case A:
               sb.append("A");
               break;
            case G:
               sb.append("G");
               break;
         }
      }
      return sb.toString();
   }

   public String GetProtinString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < epitopes.length; i++) {
         for (int j = 0; j < epitopes[0].length; j++) {
            sb.append(aaIntToStr.get(epitopes[i][j] & 0xFF));
         }
      }
      return sb.toString();
   }

   public static byte[] DecodeGenotype(String rnaString) {
      //todo - precalculate this
      int length = ((Main.simulationInfo.numEpitopes * Main.simulationInfo.codonsPerEpitope * 3 + 3) / 4);
      byte[] rna = new byte[length];
      int nucleicAcid = 0;
      for (int stringIndex = 0; stringIndex < rnaString.length(); stringIndex++) {
         char c = rnaString.charAt(stringIndex);
         int na = 0;
         switch (c) {
            case 'A':
               na = A;
               break;
            case 'U':
               na = U;
               break;
            case 'G':
               na = G;
               break;
            case 'C':
               na = C;
               break;
         }
         rna[nucleicAcid / 4] = (byte) ((rna[nucleicAcid / 4] & 0xFF) | (na << ((nucleicAcid % 4) * 2)));
         ++nucleicAcid;
      }
      return rna;
   }

   public Strain Replicate(int date) {
      int numMutations = 0;
      switch (Main.simulationInfo.mutationAlgorithm) {
         case Mutation_Approximate:
            if (Main.RANDOM.nextDouble() < Main.simulationInfo.mutationProbability * epitopes.length * epitopes[0].length * 3) {
               numMutations = 1;
            }
            break;
         case Mutation_Exact:
            numMutations = Main.DrawFromDistribution(Main.BINOMIAL_STRAIN, Main.RANDOM.nextDouble());
            break;
         case Mutation_None:
            //Leave numMutations set to 0
            break;
         default:
            throw new RuntimeException("Invalid mutation algorithm: " + Main.simulationInfo.mutationAlgorithm);
      }
      if (numMutations > 0) {
         byte[] newRNA = Arrays.copyOf(rna, rna.length);
         ArrayList<Integer> possibleSites = null;
         if (numMutations > 1) {
            possibleSites = new ArrayList<>();
            for (int i = 0; i < epitopes.length; i++) {
               for (int j = 0; j < epitopes[0].length; j++) {
                  for (int k = 0; k < 3; k++) {
                     possibleSites.add((i << 16) | (j << 8) | k);
                  }
               }
            }
            Collections.shuffle(possibleSites, Main.RANDOM);
         }
         for (int mutationCounter = 0; mutationCounter < numMutations; mutationCounter++) {
            int epitope;
            int codon;
            int naIndex;
            if (numMutations == 1) {
               epitope = Main.RANDOM.nextInt(epitopes.length);
               codon = Main.RANDOM.nextInt(epitopes[0].length);
               naIndex = Main.RANDOM.nextInt(3);
            } else {
               int temp = possibleSites.remove(0);
               epitope = (temp >> 16) & 0xFF;
               codon = (temp >> 8) & 0xFF;
               naIndex = temp & 0xFF;
            }
            int nucleicAcid = epitope * Main.simulationInfo.codonsPerEpitope * 3 + codon * 3 + naIndex;
            byte oldNA = (byte) (((rna[nucleicAcid / 4] & 0xFF) >> ((nucleicAcid % 4) * 2)) & 0x03);
            byte newNA = (byte) Main.RANDOM.nextInt(3);
            if (newNA >= oldNA) {
               ++newNA;
            }
            //Clear out the previous NA
            newRNA[nucleicAcid / 4] = (byte) ((newRNA[nucleicAcid / 4] & 0xFF) & ~(0x03 << ((nucleicAcid % 4) * 2)));
            //Write the new NA
            newRNA[nucleicAcid / 4] = (byte) ((newRNA[nucleicAcid / 4] & 0xFF) | ((newNA & 0xFF) << ((nucleicAcid % 4) * 2)));
         }
         //Create the new Strain
         Strain strain = new Strain(date, newRNA);
         if (strain.GetProtinString().contains(".")) {
            //The mutation introduced a stop codon, so it's not valid
            return this;
         }
         //Record lineage
         strain.stats.parentID = stats.id;
         //Increment the mutation counter
         strain.stats.mutations = stats.mutations + numMutations;
         return strain;
      } else {
         //No mutations
         return this;
      }
   }

   public void SaveState(DataOutput out) throws IOException {
      out.writeInt(stats.age);
      out.writeInt(stats.id);
      out.writeInt(stats.incidence);
      out.writeInt(stats.infected);
      out.writeInt(stats.mutations);
      out.writeInt(stats.parentID);
      out.writeInt(firstSeenDate);
      out.write(rna);
   }

   public static Strain LoadState(DataInput in) throws IOException {
      int age = in.readInt();
      int id = in.readInt();
      int incidence = in.readInt();
      int infected = in.readInt();
      int mutations = in.readInt();
      int parentID = in.readInt();
      int firstSeenDate = in.readInt();
      //todo - precalculate this
      int length = ((Main.simulationInfo.numEpitopes * Main.simulationInfo.codonsPerEpitope * 3 + 3) / 4);
      byte[] rna = new byte[length];
      in.readFully(rna);
      Strain strain = new Strain(firstSeenDate, rna);
      strain.stats.id = id;
      strain.stats.age = age;
      strain.stats.incidence = incidence;
      strain.stats.infected = infected;
      strain.stats.mutations = mutations;
      strain.stats.parentID = parentID;
      return strain;
   }

   @Override
   public String toString() {
      return GetProtinString();
   }

   @Override
   public int hashCode() {
      return stats.id;
   }

   @Override
   public boolean equals(Object obj) {
      if (obj == null) {
         return false;
      }
      if (getClass() != obj.getClass()) {
         return false;
      }
      final Strain other = (Strain) obj;
      if (this.stats.id != other.stats.id) {
         return false;
      }
      return true;
   }
   public byte[][] epitopes;
   public StrainStats stats;
   public byte[] rna;
   public int firstSeenDate;
   public static final int A = 0;
   public static final int U = 1;
   public static final int G = 2;
   public static final int C = 3;
   public static final int ALA = 0;
   public static final int ARG = 1;
   public static final int ASN = 2;
   public static final int ASP = 3;
   public static final int CYS = 4;
   public static final int GLN = 5;
   public static final int GLU = 6;
   public static final int GLY = 7;
   public static final int HIS = 8;
   public static final int ILE = 9;
   public static final int LEU = 10;
   public static final int LYS = 11;
   public static final int MET = 12;
   public static final int PHE = 13;
   public static final int PRO = 14;
   public static final int SER = 15;
   public static final int THR = 16;
   public static final int TRP = 17;
   public static final int TYR = 18;
   public static final int VAL = 19;
   public static final int STOP = 20;
   public static final byte[] codonToAA = new byte[64];
   public static final HashMap<Integer, String> aaIntToStr = new HashMap<>();

   static {
      codonToAA[GetCodon(U, U, U)] = PHE;
      codonToAA[GetCodon(U, U, C)] = PHE;
      codonToAA[GetCodon(U, U, A)] = LEU;
      codonToAA[GetCodon(U, U, G)] = LEU;
      codonToAA[GetCodon(U, C, U)] = SER;
      codonToAA[GetCodon(U, C, C)] = SER;
      codonToAA[GetCodon(U, C, A)] = SER;
      codonToAA[GetCodon(U, C, G)] = SER;
      codonToAA[GetCodon(U, A, U)] = TYR;
      codonToAA[GetCodon(U, A, C)] = TYR;
      codonToAA[GetCodon(U, A, A)] = STOP;
      codonToAA[GetCodon(U, A, G)] = STOP;
      codonToAA[GetCodon(U, G, U)] = CYS;
      codonToAA[GetCodon(U, G, C)] = CYS;
      codonToAA[GetCodon(U, G, A)] = STOP;
      codonToAA[GetCodon(U, G, G)] = TRP;
      codonToAA[GetCodon(C, U, U)] = LEU;
      codonToAA[GetCodon(C, U, C)] = LEU;
      codonToAA[GetCodon(C, U, A)] = LEU;
      codonToAA[GetCodon(C, U, G)] = LEU;
      codonToAA[GetCodon(C, C, U)] = PRO;
      codonToAA[GetCodon(C, C, C)] = PRO;
      codonToAA[GetCodon(C, C, A)] = PRO;
      codonToAA[GetCodon(C, C, G)] = PRO;
      codonToAA[GetCodon(C, A, U)] = HIS;
      codonToAA[GetCodon(C, A, C)] = HIS;
      codonToAA[GetCodon(C, A, A)] = GLN;
      codonToAA[GetCodon(C, A, G)] = GLN;
      codonToAA[GetCodon(C, G, U)] = ARG;
      codonToAA[GetCodon(C, G, C)] = ARG;
      codonToAA[GetCodon(C, G, A)] = ARG;
      codonToAA[GetCodon(C, G, G)] = ARG;
      codonToAA[GetCodon(A, U, U)] = ILE;
      codonToAA[GetCodon(A, U, C)] = ILE;
      codonToAA[GetCodon(A, U, A)] = ILE;
      codonToAA[GetCodon(A, U, G)] = MET;
      codonToAA[GetCodon(A, C, U)] = THR;
      codonToAA[GetCodon(A, C, C)] = THR;
      codonToAA[GetCodon(A, C, A)] = THR;
      codonToAA[GetCodon(A, C, G)] = THR;
      codonToAA[GetCodon(A, A, U)] = ASN;
      codonToAA[GetCodon(A, A, C)] = ASN;
      codonToAA[GetCodon(A, A, A)] = LYS;
      codonToAA[GetCodon(A, A, G)] = LYS;
      codonToAA[GetCodon(A, G, U)] = SER;
      codonToAA[GetCodon(A, G, C)] = SER;
      codonToAA[GetCodon(A, G, A)] = ARG;
      codonToAA[GetCodon(A, G, G)] = ARG;
      codonToAA[GetCodon(G, U, U)] = VAL;
      codonToAA[GetCodon(G, U, C)] = VAL;
      codonToAA[GetCodon(G, U, A)] = VAL;
      codonToAA[GetCodon(G, U, G)] = VAL;
      codonToAA[GetCodon(G, C, U)] = ALA;
      codonToAA[GetCodon(G, C, C)] = ALA;
      codonToAA[GetCodon(G, C, A)] = ALA;
      codonToAA[GetCodon(G, C, G)] = ALA;
      codonToAA[GetCodon(G, A, U)] = ASP;
      codonToAA[GetCodon(G, A, C)] = ASP;
      codonToAA[GetCodon(G, A, A)] = GLU;
      codonToAA[GetCodon(G, A, G)] = GLU;
      codonToAA[GetCodon(G, G, U)] = GLY;
      codonToAA[GetCodon(G, G, C)] = GLY;
      codonToAA[GetCodon(G, G, A)] = GLY;
      codonToAA[GetCodon(G, G, G)] = GLY;
      //
      aaIntToStr.put(ALA, "A");
      aaIntToStr.put(ARG, "R");
      aaIntToStr.put(ASN, "N");
      aaIntToStr.put(ASP, "D");
      aaIntToStr.put(CYS, "C");
      aaIntToStr.put(GLN, "Q");
      aaIntToStr.put(GLU, "E");
      aaIntToStr.put(GLY, "G");
      aaIntToStr.put(HIS, "H");
      aaIntToStr.put(ILE, "I");
      aaIntToStr.put(LEU, "L");
      aaIntToStr.put(LYS, "K");
      aaIntToStr.put(MET, "M");
      aaIntToStr.put(PHE, "F");
      aaIntToStr.put(PRO, "P");
      aaIntToStr.put(SER, "S");
      aaIntToStr.put(THR, "T");
      aaIntToStr.put(TRP, "W");
      aaIntToStr.put(TYR, "Y");
      aaIntToStr.put(VAL, "V");
      aaIntToStr.put(STOP, ".");
   }

   private static int GetCodon(int a, int b, int c) {
      return (a << 4) | (b << 2) | c;
   }
}
