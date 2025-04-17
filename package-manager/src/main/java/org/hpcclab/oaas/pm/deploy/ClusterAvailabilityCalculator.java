package org.hpcclab.oaas.pm.deploy;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Pawissanutt
 */
public class ClusterAvailabilityCalculator {
  /**
   * Calculates the overall availability of a Raft cluster given individual node availabilities.
   *
   * @param availabilities A list of individual node availabilities (values between 0 and 1).
   * @return The overall cluster availability as a probability between 0 and 1.
   */
  public static double calculateClusterAvailability(List<Double> availabilities) {
    int n = availabilities.size();
    int quorum = (n / 2) + 1;
    return calculateClusterAvailability(availabilities, quorum);
  }

  /**
   * Calculates the overall availability of a Raft cluster given individual node availabilities.
   *
   * @param availabilities A list of individual node availabilities (values between 0 and 1).
   * @return The overall cluster availability as a probability between 0 and 1.
   */
  public static double calculateClusterAvailability(List<Double> availabilities, int quorum) {
    int n = availabilities.size();
    double totalAvailability = 0.0;

    // Iterate over all combinations where at least 'quorum' nodes are operational
    for (int k = quorum; k <= n; k++) {
      totalAvailability += calculateProbabilityOfKNodesOperational(availabilities, k);
    }

    return totalAvailability;
  }

  /**
   * Calculates the probability that exactly k nodes are operational.
   *
   * @param availabilities A list of individual node availabilities.
   * @param k The exact number of nodes that are operational.
   * @return The probability that exactly k nodes are operational.
   */
  private static double calculateProbabilityOfKNodesOperational(List<Double> availabilities, int k) {
    int n = availabilities.size();
    double probability = 0.0;

    // Generate all combinations of k nodes out of n
    int[] indices = new int[k];
    if (initializeCombination(indices, n, k)) {
      do {
        double prob = 1.0;
        for (int i = 0; i < n; i++) {
          if (contains(indices, i)) {
            prob *= availabilities.get(i); // Node i is up
          } else {
            prob *= (1 - availabilities.get(i)); // Node i is down
          }
        }
        probability += prob;
      } while (nextCombination(indices, n, k));
    }

    return probability;
  }

  /**
   * Initializes the combination array.
   *
   * @param indices The array to store combination indices.
   * @param n Total number of items.
   * @param k Number of items to select.
   * @return True if initialization is successful.
   */
  private static boolean initializeCombination(int[] indices, int n, int k) {
    if (k > n) {
      return false;
    }
    for (int i = 0; i < k; i++) {
      indices[i] = i;
    }
    return true;
  }

  /**
   * Generates the next combination of indices.
   *
   * @param indices The current combination of indices.
   * @param n Total number of items.
   * @param k Number of items to select.
   * @return True if the next combination is generated, false if there are no more combinations.
   */
  private static boolean nextCombination(int[] indices, int n, int k) {
    int i = k - 1;
    while (i >= 0 && indices[i] == n - k + i) {
      i--;
    }
    if (i < 0) {
      return false;
    }
    indices[i]++;
    for (int j = i + 1; j < k; j++) {
      indices[j] = indices[j - 1] + 1;
    }
    return true;
  }

  /**
   * Checks if an array contains a specific value.
   *
   * @param array The array to check.
   * @param value The value to look for.
   * @return True if the value is found in the array, false otherwise.
   */
  private static boolean contains(int[] array, int value) {
    for (int j : array) {
      if (j == value) {
        return true;
      }
    }
    return false;
  }

  public static void main(String[] args) {
    final double TIER_1 = 0.99671;
    final double WORSE_TIER_1 = 1 - (1 - TIER_1) * 10;
    List<Double> nodes = new ArrayList<>();
    for (int i = 1; i <= 7; i++) {
      nodes.add(TIER_1);
      System.out.println("Number of Nodes: " + i + " Each node: " + nodes);
      var raftAvailability = calculateClusterAvailability(nodes);
//      var weakAvailability = calculateClusterAvailability(nodes, 1);
      System.out.println("Raft availability: " + raftAvailability);
//      System.out.println("Weak availability: " + weakAvailability);
    }

    nodes.clear();
    for (int i = 1; i <= 10; i++) {
      nodes.add(WORSE_TIER_1);
      System.out.println("Number of Nodes: " + i + " Each node: " + nodes);
      var raftAvailability = calculateClusterAvailability(nodes);
//      var weakAvailability = calculateClusterAvailability(nodes, 1);
      System.out.println("Raft availability: " + raftAvailability);
//      System.out.println("Weak availability: " + weakAvailability);
    }
  }
}
