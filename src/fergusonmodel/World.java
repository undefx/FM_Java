package fergusonmodel;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import stats.PatchStats;
import stats.SimulationInfo;
import stats.StatsWriter;
import stats.StrainStats;
import util.LinkedHashMap;
import util.LinkedHashSet;

public class World {

   public World() {
      //Initial variable values
      date = 0;
      knownStrains = new LinkedHashMap<>();
      patches = new Patch[Main.simulationInfo.numPatches];
      //Initialize the patches
      for (int i = 0; i < patches.length; i++) {
         if (i == 0) {
            //Allocate and load a new neighborhood map
            patches[i] = new Patch(Main.simulationInfo, i, null);
         } else {
            //Resuse the existing neighborhood map
            patches[i] = new Patch(Main.simulationInfo, i, patches[0].hosts);
         }
      }
   }

   public int GetDate() {
      return date;
   }

   public String GetDateString() {
      return String.format("%04d-%02d-%02d", date / 365, (date % 365) / 31 + 1, (date % 365 % 31) + 1);
   }

   public static double GetCrossProtection(double d) {
      if (d >= Main.simulationInfo.nt) {
         return Main.simulationInfo.theta1 + (Main.simulationInfo.theta0 - Main.simulationInfo.theta1) * (d - Main.simulationInfo.nt) / (Main.simulationInfo.numEpitopes * Main.simulationInfo.codonsPerEpitope - Main.simulationInfo.nt);
      } else if (d > 0) {
         return Main.simulationInfo.theta1;
      } else {
         return 1;
      }
   }

   public static double GetInfectionProbability(World world, Host host, Strain strain) {
      double a = 1;
      double b = 1;
      if (host.lastInfectionDate != Integer.MIN_VALUE) {
         a = 1 - Main.simulationInfo.omega * Math.exp(-(world.date - host.lastInfectionDate) / Main.simulationInfo.tau);
         b = 1 - GetCrossProtection(host.GetImmunityDistance(strain));
      }
      return a * b;
   }

   public void Update() {
      ++date;
      //Happy Birthday
      long totalSick = GetTotalSick();
      for (int i = (date - 1) % Main.simulationInfo.hostLifespan; i < patches[0].hosts.length; i += Main.simulationInfo.hostLifespan) {
         for (Patch patch : patches) {
            Host host = patch.hosts[i];
            //See if this host was sick yesterday (the sick count is up-to-date as of the end of yesterday)
            boolean wasSick = host.IsSick(date - 1);
            //Don't let all sick hosts be reset
            if (!wasSick || totalSick > Main.simulationInfo.minCarriers) {
               //Pointer cleanup
               if (host.lastInfectionStrain != null && knownStrains.Contains(host.lastInfectionStrain)) {
                  knownStrains.Get(host.lastInfectionStrain).Remove(host);
               }
               //Update the sick count
               if (wasSick) {
                  --totalSick;
               }
               host.Reset();
            }
         }
      }
      //Reset daily patch stats
      for (Patch patch : patches) {
         patch.stats.incidence = 0;
      }
      //Reset daily strain stats
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         strain.stats.incidence = 0;
      }
      //Mutate the virus in each infected host for this day
      LinkedHashMap<Strain, LinkedHashSet<Host>> newStrains = new LinkedHashMap<>();
      Deque<Host> oldInfections = new LinkedList<>();
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         oldInfections.clear();
         for (LinkedHashSet.MapElement<Host> element2 = knownStrains.Get(strain).GetRoot(); element2 != null; element2 = element2.next) {
            Host infectedHost = element2.key;
            //Replicate the virus
            Strain newStrain = infectedHost.lastInfectionStrain.Replicate(date);
            if (newStrain != infectedHost.lastInfectionStrain) {
               //The virus has mutated
               oldInfections.addFirst(infectedHost);
               infectedHost.Infect(infectedHost.lastInfectionDate, newStrain);
               if (!newStrains.Contains(newStrain)) {
                  newStrains.Put(newStrain, new LinkedHashSet<Host>());
               }
               newStrains.Get(newStrain).Add(infectedHost);
            }
         }
         knownStrains.Get(strain).RemoveAll(oldInfections);
      }
      //Merge new strains with existing strains
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = newStrains.GetRoot(); element != null; element = element.next) {
         Strain newStrain = element.key;
         knownStrains.Put(newStrain, newStrains.Get(newStrain));
      }
      //Infect
      Deque<Host> lastRecoveredHosts = new LinkedList<>();
      Deque<Strain> extinctStrains = new LinkedList<>();
      Deque<Host> newInfections = new LinkedList<>();
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         newInfections.clear();
         oldInfections.clear();
         for (LinkedHashSet.MapElement<Host> element2 = knownStrains.Get(strain).GetRoot(); element2 != null; element2 = element2.next) {
            Host infectedHost = element2.key;
            if (!infectedHost.IsInfectious(date)) {
               if (!infectedHost.IsIncubating(date)) {
                  oldInfections.addFirst(infectedHost);
               }
               continue;
            }
            //Calculate how many people this person will infect today
            ArrayList<Host> potentialInfections = new ArrayList<>();
            //Find out where this host lives, for seasonality purposes
            int deme = 0;
            switch (Main.simulationInfo.connectivityAlgorithm) {
               case Connectivity_Flat:
               case Connectivity_Cylindrical:
                  if (infectedHost.patchID >= patches.length / 2) {
                     //Southern hemisphere
                     deme = 2;
                  } else {
                     //Northern hemisphere
                     deme = 0;
                  }
                  break;
               case Connectivity_Tropics:
                  if (infectedHost.patchID < patches.length * 4 / 10) {
                     //North
                     deme = 0;
                  } else if (infectedHost.patchID < patches.length * 9 / 10) {
                     //Tropics
                     deme = 1;
                  } else {
                     //South
                     deme = 2;
                  }
                  break;
               default:
                  throw new RuntimeException("Invalid connectivity algorithm: " + Main.simulationInfo.connectivityAlgorithm);
            }
            //Get the day of the year for seasonality
            int dayOfYear;
            if (deme == 0) {
               //Standard seasonality for the north
               dayOfYear = date % 365;
            } else if (deme == 1) {
               //There is no seasonality in the tropics (as if it were always April 1, using cosine)
               dayOfYear = 365 / 4;
            } else {
               //Opposite seasonality for the south
               dayOfYear = (date + 365 / 2) % 365;
            }
            //Exposures between patches - drawing from a poisson and sampling with replacement
            int numGlobal = Main.DrawFromDistribution(Main.POISSON_GLOBAL[dayOfYear], Main.RANDOM.nextDouble());
            int patchesPerRow = Main.simulationInfo.numPatches / 4;
            for (int i = 0; i < numGlobal; i++) {
               int patchID = Main.RANDOM.nextInt(patches.length - 1);
               if (patchID >= infectedHost.patchID) {
                  ++patchID;
               }
               boolean allowed = false;
               switch (Main.simulationInfo.connectivityAlgorithm) {
                  case Connectivity_Flat:
                  case Connectivity_Tropics:
                     //Allow the infection to spread to any other patch
                     allowed = true;
                     break;
                  case Connectivity_Cylindrical:
                     //Only allow the infection to spread to neighboring patches
                     int row1 = infectedHost.patchID / patchesPerRow;
                     int col1 = infectedHost.patchID % patchesPerRow;
                     int row2 = patchID / patchesPerRow;
                     int col2 = patchID % patchesPerRow;
                     //See if the patches are neighbors
                     if (col1 == col2 && Math.abs(row1 - row2) == 1) {
                        allowed = true;
                     } else if (row1 == row2) {
                        int delta = (patchesPerRow + (col1 - col2)) % patchesPerRow;
                        if (delta == 1 || delta == patchesPerRow - 1) {
                           allowed = true;
                        }
                     }
                     break;
                  default:
                     throw new RuntimeException("Invalid connectivity algorithm: " + Main.simulationInfo.connectivityAlgorithm);
               }
               if (allowed) {
                  int hostID = Main.RANDOM.nextInt(patches[patchID].hosts.length);
                  potentialInfections.add(patches[patchID].hosts[hostID]);
               }
            }
            //Exposures within this patch - drawing from a poisson and sampling with replacement
            int numPatch = Main.DrawFromDistribution(Main.POISSON_PATCH[dayOfYear], Main.RANDOM.nextDouble());
            for (int i = 0; i < numPatch; i++) {
               int patchID = infectedHost.patchID;
               int hostID = Main.RANDOM.nextInt(patches[patchID].hosts.length);
               potentialInfections.add(patches[patchID].hosts[hostID]);
            }
            //Exposures in the local neighborhood - algorithm is determined by Main.INFECTION_ALGORITHM
            if (infectedHost.numNeighbors > 0) {
               int numLocal = 0;
               //First, calculate the number of neighbors that should be exposed
               switch (Main.simulationInfo.infectionAlgorithm) {
                  case Infection_Exact:
                     //Nothing to do here
                     break;
                  case Infection_Approximate1:
                     //As an optimization, just draw the number of neighbors to expose from a poisson
                     numLocal = Main.DrawFromDistribution(Main.POISSON_LOCAL[dayOfYear], Main.RANDOM.nextDouble());
                     break;
                  case Infection_Approximate2:
                  case Infection_Approximate3:
                     //This is a copy of Anuroop's implementation
                     double temp = Main.INFECTION_LOCAL[dayOfYear] * infectedHost.numNeighbors;
                     if (Main.RANDOM.nextDouble() < temp - (int) temp) {
                        numLocal = (int) temp + 1;
                     } else {
                        numLocal = (int) temp;
                     }
                     break;
                  default:
                     throw new RuntimeException("Invalid infection algorithm: " + Main.simulationInfo.infectionAlgorithm);
               }
               //Next, sample the neighbors to expose
               SimulationInfo.Algorithm infectionAlgorithm = Main.simulationInfo.infectionAlgorithm;
               if (Main.simulationInfo.infectionAlgorithm == SimulationInfo.Algorithm.Infection_Approximate3 && numLocal <= 1) {
                  //With just 1 (or 0) neighbor to expose, revert to sampling with replacement
                  //It's more efficient, and the results are the exact same
                  infectionAlgorithm = SimulationInfo.Algorithm.Infection_Approximate2;
               }
               switch (infectionAlgorithm) {
                  case Infection_Exact:
                     //Query each neighbor to see if they should be exposed
                     double probability = Main.LOCAL_INFECTION_PROBABILITY;
                     if (deme != 1) {
                        //Take seasonality into account if this host isn't in the tropics
                        //Using cosine so the peak is January 1
                        probability *= (1 + Main.simulationInfo.seasonalityMultiplier * Math.cos((double) dayOfYear / 365.0 * Math.PI * 2.0));
                     }
                     //Try to expose all the neighbors
                     for (int i = 0; i < infectedHost.numNeighbors; i++) {
                        int neighborID = Host.neighborList[infectedHost.neighborIndex + i];
                        if (Main.RANDOM.nextDouble() < probability) {
                           potentialInfections.add(patches[infectedHost.patchID].hosts[neighborID]);
                        }
                     }
                     break;
                  case Infection_Approximate1:
                  case Infection_Approximate2:
                     //Sampling with replacement (fast, but can sample the same neighbor many times)
                     for (int i = 0; i < numLocal; i++) {
                        int neighborID = Host.neighborList[infectedHost.neighborIndex + Main.RANDOM.nextInt(infectedHost.numNeighbors)];
                        potentialInfections.add(patches[infectedHost.patchID].hosts[neighborID]);
                     }
                     break;
                  case Infection_Approximate3:
                     //Sampling without replacement (using a copy of this host's neighbor list)
                     ArrayList<Integer> neighborIDs = new ArrayList<>();
                     for (int i = 0; i < infectedHost.numNeighbors; i++) {
                        int neighborID = Host.neighborList[infectedHost.neighborIndex + i];
                        neighborIDs.add(neighborID);
                     }
                     for (int i = 0; i < numLocal; i++) {
                        potentialInfections.add(patches[infectedHost.patchID].hosts[neighborIDs.remove(Main.RANDOM.nextInt(neighborIDs.size()))]);
                     }
                     break;
                  default:
                     throw new RuntimeException("Invalid infection algorithm: " + Main.simulationInfo.infectionAlgorithm);
               }
            }
            //Attempt to infect everyone who was exposed above
            for (Host host : potentialInfections) {
               //There is a chance of immunity
               double infectionProbability = GetInfectionProbability(this, host, infectedHost.lastInfectionStrain);
               double d = Main.RANDOM.nextDouble();
               if (d < infectionProbability) {
                  //Infected
                  if (host.lastInfectionStrain != null && host.lastInfectionStrain != infectedHost.lastInfectionStrain) {
                     if (knownStrains.Contains(host.lastInfectionStrain)) {
                        knownStrains.Get(host.lastInfectionStrain).Remove(host);
                     }
                  }
                  host.Infect(date, infectedHost.lastInfectionStrain);
                  newInfections.addFirst(host);
                  //Update incidence statistics for patches and strains
                  patches[host.patchID].stats.incidence++;
                  infectedHost.lastInfectionStrain.stats.incidence++;
               } else {
                  //Only exposed
                  if (!host.IsIncubating(date) && !host.IsInfectious(date)) {
                     if (host.lastInfectionDate != Integer.MIN_VALUE) {
                        //Boost pre-existing immune responses
                        host.lastInfectionDate = Math.max(host.lastInfectionDate, date - 6);
                     }
                  }
               }
            }
         }
         knownStrains.Get(strain).AddAll(newInfections);
         knownStrains.Get(strain).RemoveAll(oldInfections);
         for (Host host : oldInfections) {
            lastRecoveredHosts.addFirst(host);
         }
         if (knownStrains.Get(strain).GetSize() == 0) {
            extinctStrains.addFirst(strain);
         }
      }
      for (Strain strain : extinctStrains) {
         knownStrains.Remove(strain);
      }
      totalSick = GetTotalSick();
      if (totalSick < Main.simulationInfo.minCarriers) {
         //Roni - "Now introduce the following modification:  if the person about to recover will leave no one [in] state I (infectious) or state E (exposed), don't let them recover that day."
         //Me - Keeping a minimum viral reservoir of SimulationInfo.minCarriers
         ArrayList<Host> lastRecoveredHostsList = new ArrayList<>();
         lastRecoveredHostsList.addAll(lastRecoveredHosts);
         int index = 0;
         while (index < lastRecoveredHostsList.size() && totalSick < Main.simulationInfo.minCarriers) {
            ++totalSick;
            Host host = lastRecoveredHostsList.get(index++);
            host.lastInfectionDate = date;
            if (!knownStrains.Contains(host.lastInfectionStrain)) {
               knownStrains.Put(host.lastInfectionStrain, new LinkedHashSet<Host>());
            }
            knownStrains.Get(host.lastInfectionStrain).Add(host);
         }
      }
      //Maybe Update Patch Stats
      if (Main.simulationInfo.patchStatsAlgorithm == SimulationInfo.Algorithm.PatchStats_All) {
         for (Patch patch : patches) {
            patch.stats.naive = 0;
            patch.stats.exposed = 0;
            patch.stats.infectious = 0;
            patch.stats.recovered = 0;
            for (Host host : patch.hosts) {
               if (host.lastInfectionDate == Integer.MIN_VALUE) {
                  patch.stats.naive++;
               } else if ((date - host.lastInfectionDate) < 2) {
                  patch.stats.exposed++;
               } else if ((date - host.lastInfectionDate) < 6) {
                  patch.stats.infectious++;
               } else {
                  patch.stats.recovered++;
               }
            }
         }
      }
      //Update strain stats
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         //Update strain stats for total infections
         strain.stats.infected = knownStrains.Get(strain).GetSize();
         //Update strain stats for strain age
         strain.stats.age = date - strain.firstSeenDate;
      }
   }

   public void UpdateStats(StatsWriter statsWriter, int pauseDate) throws IOException {
      PatchStats[] patchStats = new PatchStats[patches.length];
      for (int i = 0; i < patchStats.length; i++) {
         patchStats[i] = patches[i].stats;
      }
      StrainStats[] strainStats = new StrainStats[knownStrains.GetSize()];
      int index = 0;
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         strainStats[index++] = strain.stats;
      }
      statsWriter.SaveDay(date - pauseDate, patchStats, strainStats);
//      Main.Print("%d-%d %08x %d", date, pauseDate, Main.RANDOM.nextInt(), GetTotalSick());
   }

   public long GetTotalSick() {
      long count = 0;
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         count += knownStrains.Get(strain).GetSize();
      }
      return count;
   }

   public void SaveState(DataOutput out) throws IOException {
      out.writeInt(date);
      //Get a set of old strains
      LinkedHashSet<Strain> otherStrains = new LinkedHashSet<>();
      for (int p = 0; p < Main.simulationInfo.numPatches; p++) {
         for (int h = 0; h < Main.simulationInfo.hostsPerPatch; h++) {
            Strain strain = patches[p].hosts[h].lastInfectionStrain;
            if (strain != null && !knownStrains.Contains(strain)) {
               otherStrains.Add(strain);
            }
         }
      }
      //Save all strains here
      out.writeInt(knownStrains.GetSize() + otherStrains.GetSize());
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> element = knownStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         strain.SaveState(out);
      }
      for (LinkedHashSet.MapElement<Strain> element = otherStrains.GetRoot(); element != null; element = element.next) {
         Strain strain = element.key;
         strain.SaveState(out);
      }
      //Save all hosts here
      for (int i = 0; i < patches.length; i++) {
         patches[i].SaveState(out);
      }
      //With all strains and hosts saved, save the infection hash maps
      out.writeInt(knownStrains.GetSize());
      for (LinkedHashMap.MapElement<Strain, LinkedHashSet<Host>> strainElement = knownStrains.GetRoot(); strainElement != null; strainElement = strainElement.next) {
         Strain strain = strainElement.key;
         LinkedHashSet<Host> hostSet = strainElement.value;
         out.writeInt(strain.stats.id);
         out.writeInt(hostSet.GetSize());
         for (LinkedHashSet.MapElement<Host> hostElement = hostSet.GetRoot(); hostElement != null; hostElement = hostElement.next) {
            Host host = hostElement.key;
            out.writeInt(host.hashCode());
         }
      }
   }

   public void LoadState(DataInput in) throws IOException {
      date = in.readInt();
      //Load all strains here
      int numStrains = in.readInt();
      LinkedHashMap<Integer, Strain> strains = new LinkedHashMap<>();
      for (int i = 0; i < numStrains; i++) {
         Strain strain = Strain.LoadState(in);
         strains.Put(strain.stats.id, strain);
      }
      //Load all hosts here
      for (int i = 0; i < patches.length; i++) {
         patches[i].LoadState(in, strains);
      }
      //With all strains and hosts loaded, load the infection hash maps
      int num = in.readInt();
      for (int i = 0; i < num; i++) {
         int strainID = in.readInt();
         Strain strain = strains.Get(strainID);
         int numHosts = in.readInt();
         LinkedHashSet<Host> hosts = new LinkedHashSet<>();
         for (int j = 0; j < numHosts; j++) {
            int id = in.readInt();
            int patchID = (id >> 24) & 0xFF;
            int hostID = id & 0xFFFFFF;
            Host host = patches[patchID].hosts[hostID];
            hosts.Add(host);
         }
         //Saved and loaded as a stack, so the entries all backwards now
         hosts.Reverse();
         knownStrains.Put(strain, hosts);
      }
      //Saved and loaded as a stack, so the entries all backwards now
      knownStrains.Reverse();
   }
   protected Patch[] patches;
   protected int date;
   protected LinkedHashMap<Strain, LinkedHashSet<Host>> knownStrains;
}
