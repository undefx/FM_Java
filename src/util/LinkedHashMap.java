package util;

import java.util.ArrayList;
import java.util.List;

public class LinkedHashMap<K, V> {

   public static final int DEFAULT_BINS = 53;

   public String Debug() {
      return String.format("[Size: %d | Bins: %d | Usage: %.2f/%.2f=%.2f]", size, bins.length, GetAverageLookupTime(), GetIdealLookupTime(), GetLookupTimeRatio());
   }

   public LinkedHashMap() {
      this(DEFAULT_BINS);
   }

   @SuppressWarnings({"unchecked"})
   public LinkedHashMap(int numBins) {
      bins = new Bin[numBins];
      root = null;
      size = 0;
      for (int i = 0; i < bins.length; i++) {
         bins[i] = new Bin<>();
      }
   }

   public void Put(K key, V value) {
      Remove(key);
      MapElement<K, V> e = new MapElement<>(key, value, root);
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
      for (MapElement<K, V> e : bins[id % bins.length].list) {
         if (e.key.hashCode() == id) {
            return true;
         }
      }
      return false;
   }

   public V Get(K key) {
      int id = key.hashCode();
      for (MapElement<K, V> e : bins[id % bins.length].list) {
         if (e.key.hashCode() == id) {
            return e.value;
         }
      }
      return null;
   }

   public void Remove(K key) {
      int id = key.hashCode();
      for (int i = 0; i < bins[id % bins.length].list.size(); i++) {
         MapElement<K, V> e = bins[id % bins.length].list.get(i);
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
      for (MapElement<K, V> e = root; e != null; e = e.next) {
         bins[e.key.hashCode() % bins.length].list.add(e);
      }
   }

   public void Reverse() {
      MapElement<K, V> node = root;
      while (node != null) {
         root = node;
         MapElement<K, V> next = node.next;
         node.next = node.prev;
         node = node.prev = next;
      }
   }

   public List<K> GetKeys() {
      List<K> list = new ArrayList<>();
      for (MapElement<K, V> e = root; e != null; e = e.next) {
         list.add(e.key);
      }
      return list;
   }

   public MapElement<K, V> GetRoot() {
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

   public static class MapElement<K, V> {

      public MapElement(K key, V value, MapElement<K, V> next) {
         this.key = key;
         this.value = value;
         this.prev = null;
         this.next = next;
      }
      public K key;
      public V value;
      public MapElement<K, V> prev, next;
   }

   protected static class Bin<K, V> {

      public Bin() {
         list = new ArrayList<>();
      }
      public List<MapElement<K, V>> list;
   }
   protected Bin<K, V>[] bins;
   protected MapElement<K, V> root;
   protected int size;
}
