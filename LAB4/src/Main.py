from county import Dataset
from clustering import kmeans_clustering
from clustering import hierarchical_clustering
from plotter import draw_clustering

print("Script start...")

print("Reading dataset...")
dataset_full = Dataset.read_from_file("inputFiles/unifiedCancerData_3108.csv")
dataset_212 = Dataset.read_from_file("inputFiles/unifiedCancerData_212.csv")
dataset_562 = Dataset.read_from_file("inputFiles/unifiedCancerData_562.csv")
dataset_1041 = Dataset.read_from_file("inputFiles/unifiedCancerData_1041.csv")

print("Computing...")
C1, t1, d1 = kmeans_clustering(dataset_562, 16, 5)
print("Time spent for kmeans clustering:", t1)
print("Distortion for kmeans clustering:", d1)
print("Drawing...")
draw_clustering(C1)

print("Computing...")
C2, t2, d2 = hierarchical_clustering(dataset_562, 16)
print("Time spent for hierchical clustering:", t2)
print("Distortion for hierchical clustering:", d2)
print("Drawing...")
draw_clustering(C2)

print("Script end")
