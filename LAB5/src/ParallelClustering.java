import javafx.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ParallelClustering {

    private class ParallelReduceCluster extends RecursiveTask<Pair<Pair<Double, Double>, Integer>> {
        private List<Integer> cluster; // contiene per ciascun punto l'indice del cluster a cui appartiene
        private final int start; // indice iniziale
        private final int end; // indice finale
        private final int h;
        private List<City> cities;

        ParallelReduceCluster(List<Integer> cluster, int start, int end, int h, List<City> cities) {
            this.cluster = cluster;
            this.start = start;
            this.end = end;
            this.h = h;
            this.cities = cities;
        }

        protected Pair<Pair<Double, Double>, Integer> compute() {
            if (start == end) {
                if (cluster.get(start) == h)
                    return new Pair<>(new Pair<>(cities.get(start).getLatitude(), cities.get(start).getLongitude()), 1);
                else
                    return new Pair<>(new Pair<>(0d, 0d), 0);
            }
            else {
                int mid = (start + end) / 2;
                ParallelReduceCluster p1 = new ParallelReduceCluster(cluster, start, mid, h, cities);
                p1.fork();
                ParallelReduceCluster p2 = new ParallelReduceCluster(cluster, mid + 1, end, h, cities);
                Pair<Pair<Double, Double>, Integer> res2 = p2.compute();
                Pair<Pair<Double, Double>, Integer> res1 = p1.join();
                double sum_lat = res1.getKey().getKey() + res2.getKey().getKey();
                double sum_lon = res1.getKey().getValue() + res2.getKey().getValue();
                int size = res1.getValue() + res2.getValue();
                return new Pair<>(new Pair<>(sum_lat, sum_lon), size);
            }
        }
    }

    private class FirstParallelFor extends RecursiveTask<Void> {
        private List<City> cities;
        private List<Point> centroid;
        private List<Integer> cluster;
        private final int start; // indice iniziale
        private final int end; // indice finale

        public FirstParallelFor(List<City> cities, List<Point> centroid, List<Integer> cluster, int start, int end) {
            this.cities = cities;
            this.centroid = centroid;
            this.cluster = cluster;
            this.start = start;
            this.end = end;
        }
            private int getMinCentroid(List<Point> centroid, Point min) {
            int minDistance = centroid.get(0).getDistance(min);
            int pos = 0;
            for (int i = 1; i < centroid.size(); i++) {
                int curDistance = centroid.get(i).getDistance(min);
                if (curDistance < minDistance) {
                    minDistance = curDistance;
                    pos = i;
                }
            }
            return pos;
        }
        protected Void compute() {
            if (start == end) {
                int l = getMinCentroid(centroid, cities.get(start));
                cluster.set(start, l);
            }
            else {
                int middle = (start + end) / 2;
                FirstParallelFor p1 = new FirstParallelFor(cities, centroid, cluster, start, middle);
                p1.fork();
                FirstParallelFor p2 = new FirstParallelFor(cities, centroid, cluster, middle+1, end);
                p2.compute();
                p1.join();
            }
            return null;
        }
    }

    private class SecondParallelFor extends RecursiveTask<Void>{
        private List<Integer> cluster;
        private final int start; // indice iniziale
        private final int end; // indice finale
        private List<City> cities;
        private List<Point> centroid;

        SecondParallelFor(List<Integer> cluster, int start, int end, List<City> cities, List<Point> centroid) {
            this.cluster = cluster;
            this.start = start;
            this.end = end;
            this.cities = cities;
            this.centroid = centroid;
        }

        public Void compute(){
            if (start == end) {
                ParallelReduceCluster task = new ParallelReduceCluster(cluster, 0, cluster.size() - 1, start, cities);
                Pair<Pair<Double, Double>, Integer> res = task.compute();
                double sum_lat = res.getKey().getKey();
                double sum_lon = res.getKey().getValue();
                int size = res.getValue();
                if (size == 0)
                    centroid.set(start, new Point(0, 0));
                else
                    centroid.set(start, new Point(sum_lat / size, sum_lon / size));
            }
            else {
                int middle = (start + end) / 2;
                SecondParallelFor p1 = new SecondParallelFor(cluster, start, middle, cities, centroid);
                p1.fork();
                SecondParallelFor p2 = new SecondParallelFor(cluster, middle + 1, end, cities, centroid);
                p2.compute();
                p1.join();
            }
            return null;
        }
    }

    public List<Integer> parallelKMeansClustering(List<City> cities, int clustNumber, int iterations) {
        ForkJoinPool commonPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

        // centroids[h] associa al cluster C_h il suo centroide
        // (i centroidi iniziali sono le 'clustNumber' contee più popolose)
        List<Point> centroid = cities.stream()
                .sorted(Comparator.comparing(City::getPopulation).reversed())
                .limit(clustNumber).collect(Collectors.toList());
        // cluster[j] associa city[j] all'indice l del cluster C_l a cui appartiene
        List<Integer> cluster = new ArrayList<>(Collections.nCopies(cities.size(), 0));

        for (int i = 0; i < iterations; i++) {

            //First parallel for
            FirstParallelFor firstTask = new FirstParallelFor(cities, centroid, cluster, 0,cities.size()-1);
            //firstTask.compute();
            commonPool.invoke(firstTask); //execute and join first parallel for

            //Second parallel for
            SecondParallelFor secondTask = new SecondParallelFor(cluster, 0, centroid.size()-1, cities, centroid);
            commonPool.invoke(secondTask); //execute and join second parallel for

        }
        return cluster;
    }
}
