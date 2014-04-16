package util;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class LinkedHashSet<K> {

   public static final int DEFAULT_BINS = 53;

   public String Debug() {
      return String.format("[Size: %d | Bins: %d | Usage: %.2f/%.2f=%.2f]", size, bins.length, GetAverageLookupTime(), GetIdealLookupTime(), GetLookupTimeRatio());
   }

   public LinkedHashSet() {
      this(DEFAULT_BINS);
   }

   @SuppressWarnings({"unchecked"})
   public LinkedHashSet(int numBins) {
      bins = new Bin[numBins];
      root = null;
      size = 0;
      for (int i = 0; i < bins.length; i++) {
         bins[i] = new Bin<>();
      }
   }

   public void Add(K key) {
      Remove(key);
      MapElement<K> e = new MapElement<>(key, root);
      if (root != null) {
         root.prev = e;
      }
      root = e;
      bins[key.hashCode() % bins.length].list.add(e);
      ++size;
      if (size > bins.length * .8) {
         Resize(bins.length * 4 + 1);
      }
   }

   public boolean Contains(K key) {
      int id = key.hashCode();
      for (MapElement<K> e : bins[id % bins.length].list) {
         if (e.key.hashCode() == id) {
            return true;
         }
      }
      return false;
   }

   public void Remove(K key) {
      int id = key.hashCode();
      for (int i = 0; i < bins[id % bins.length].list.size(); i++) {
         MapElement<K> e = bins[id % bins.length].list.get(i);
         if (e.key.hashCode() == id) {
            bins[id % bins.length].list.remove(i);
            if (e.prev != null) {
               e.prev.next = e.next;
            }
            if (e.next != null) {
               e.next.prev = e.prev;
            }
            if (e == root) {
               root = e.next;
            }
            --size;
            if (size < bins.length * .05 && bins.length > DEFAULT_BINS * 5) {
               int numBins = bins.length / 5;
               Resize(numBins - (numBins % 2 == 0 ? 1 : 0));
            }
            return;
         }
      }
   }

   @SuppressWarnings({"unchecked"})
   public void Resize(int numBins) {
      bins = new Bin[numBins];
      for (int i = 0; i < bins.length; i++) {
         bins[i] = new Bin<>();
      }
      for (MapElement<K> e = root; e != null; e = e.next) {
         bins[e.key.hashCode() % bins.length].list.add(e);
      }
   }

   public void Reverse() {
      MapElement<K> node = root;
      while (node != null) {
         root = node;
         MapElement<K> next = node.next;
         node.next = node.prev;
         node.prev = next;
         node = node.prev = next;
      }
   }

   public void AddAll(Deque<K> x) {
      for (K key : x) {
         Add(key);
      }
   }

   public void AddAll(LinkedHashSet<K> x) {
      for (MapElement<K> e = x.root; e != null; e = e.next) {
         Add(e.key);
      }
   }

   public void RemoveAll(Deque<K> x) {
      for (K key : x) {
         Remove(key);
      }
   }

   public void RemoveAll(LinkedHashSet<K> x) {
      for (MapElement<K> e = x.root; e != null; e = e.next) {
         Remove(e.key);
      }
   }

   public void Clear() {
      for (int i = 0; i < bins.length; i++) {
         bins[i].list.clear();
      }
      root = null;
      size = 0;
   }

   public List<K> GetElements() {
      List<K> list = new ArrayList<>();
      for (MapElement<K> e = root; e != null; e = e.next) {
         list.add(e.key);
      }
      return list;
   }

   public MapElement<K> GetRoot() {
      return root;
   }

   public int GetSize() {
      return size;
   }

   public double GetAverageLookupTime() {
      if (size == 0) {
         return 0;
      }
      int sum = 0;
      for (int i = 0; i < bins.length; i++) {
         if (bins[i].list.size() > 0) {
            sum += (bins[i].list.size() * (bins[i].list.size() + 1)) / 2;
         }
      }
      return sum / (double) size;
   }

   public double GetIdealLookupTime() {
      if (size == 0) {
         return 0;
      }
      int a = size / bins.length;
      int b = size % bins.length;
      int sum = 0;
      int slots = Math.min(size, bins.length);
      for (int i = 0; i < slots; i++) {
         int temp = a;
         while (temp > 0) {
            sum += temp--;
         }
         if (i < b) {
            sum += a + 1;
         }
      }
      return sum / (double) size;
   }

   public double GetLookupTimeRatio() {
      double ideal = GetIdealLookupTime();
      if (ideal == 0) {
         return 0;
      }
      return GetAverageLookupTime() / ideal;
   }

   public static class MapElement<K> {

      public MapElement(K key, MapElement<K> next) {
         this.key = key;
         this.prev = null;
         this.next = next;
      }
      public K key;
      public MapElement<K> prev, next;
   }

   protected static class Bin<K> {

      public Bin() {
         list = new ArrayList<>();
      }
      public List<MapElement<K>> list;
   }
   protected Bin<K>[] bins;
   protected MapElement<K> root;
   protected int size;
}
