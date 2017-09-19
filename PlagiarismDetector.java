/* Author: Chi Zhang
 * Email: czhang2@scu.edu
 * Date: 04/16/2017
 */


import java.io.*;
import java.util.*;

public class PlagirismDetector {

    public static void main(String[] args) throws IOException {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String s;


        // First, read the list of files to compare
        List<String> file_list = new ArrayList<String>();
        while ((s = in.readLine()) != null) {
            // http://stackoverflow.com/questions/7899525/how-to-split-a-string-by-space
            String[] files = s.split("\\s+");
            for (int i = 0; i < files.length; i++) {
                file_list.add(files[i]);
            }
        }


        System.out.println("The files are: ");
        for (int i = 0; i < file_list.size(); i++) {
            System.out.println(file_list.get(i));
        }
        System.out.println();


        int k = 9;

        Map<String, int[]> map = new LinkedHashMap<String, int[]>();
        String[] file_content = new String[file_list.size()];
        List<List<String>> shinStorage = new ArrayList<List<String>>();

        // Then, read the content of each of the files
        for (int i = 0; i < file_list.size(); i++) {
            String file_path = "/home/mwang2/test/coen281/" + file_list.get(i);
            //String file_path = "test/" + file_list.get(i);
            //String file_path = file_list.get(i);

            FileReader fr;
            try {
                fr = new FileReader(file_path);
            } catch (Exception ex) {
                // when encounter exception, store an empty string in file_content
                System.out.println("Exception: ");
                System.out.println(ex);
                System.out.println();
                file_content[i] = "";
                continue;
            }

            BufferedReader reader = new BufferedReader(fr);
            while ((s = reader.readLine()) != null) {
                if (s.trim().length() == 0) {
                    file_content[i] += " ";
                }
                else {
                    file_content[i] += s;
                }
            }
        }

        for (int i = 0; i < file_content.length; i++) {
            List<String> shingles = shingles(file_content[i], k);
            shinStorage.add(i, shingles);
            for (int j = 0; j < shingles.size(); j++) {

                String key = shingles.get(j);
                if (map.containsKey(key)) {
                    int[] arr = map.get(key);
                    arr[i] = 1;
                    map.put(key, arr);
                }
                else {
                    int[] arr = new int[file_list.size()];
                    arr[i] = 1;
                    map.put(key, arr);
                }
            }
        }

        System.out.print("Number of Shingles: ");
        System.out.println(map.size());
        if (map.size() == 0) {
            // exit the program is there is no content
            System.out.println("No content. Program exits");
            return;
        }
        System.out.println();

        // Print out the list of shingles
        System.out.println("Shingles: ");
        for (String key: map.keySet()) {
            System.out.print(key + ": ");

            int[] arr = map.get(key);
            for (int i = 0; i < arr.length; i++) {
                System.out.print(arr[i] + " ");
            }
            System.out.println();

        }
        System.out.println();


        // Create the boolean matrix
        int[][] matrix = new int[map.size()][file_list.size()];
        int m = 0;
        for (String key: map.keySet()) {
            matrix[m] = map.get(key);
            m++;
        }


        // LSH
        // (d1, d2, p1, p2)
        int r = 0;
        int b = 0;

        double d1 = 0.2;
        double d2 = 0.8;
        double p1 = 1 - d1;
        double p2 = 1 - d2;

        double p1_bound = 0.997;
        double p2_bound = 0.003;

        int perm = (int)Math.floor(Math.sqrt(map.size()));

        // find out the suitable b and r for the signature
        boolean conti = true;
        do {
            // increment perm when no pairs of r and b found
            perm += 1;
            // try different pairs of r and b
            // test sensitivity
            for (int bi = 1; bi <= perm; bi++) {
                if (perm % bi == 0) {
                    int ri = perm / bi;
                    double p1_new = constrOR(bi,constrAND(ri, p1));
                    double p2_new = constrOR(bi,constrAND(ri, p2));

                    if (p1_new > p1_bound && p2_new < p2_bound) {
                        conti = false;
                        System.out.print("\nSensitivity:");
                        System.out.println("(" + d1 + ", " + d2 + ", " + roundNum(p1_new) + ", " + roundNum(p2_new) + ")");
                        r = ri;
                        b = bi;
                        break;
                    }
                }
            }

        } while (conti);

        System.out.println("Number of Permutation = " + perm);
        System.out.println("Rows = " + r);
        System.out.println("Bands = " + b);
        System.out.println();


        // create signature using random permutations
        int[][] signature = new int[perm][file_list.size()];

        // http://stackoverflow.com/questions/5505927/how-to-generate-a-random-permutation-in-java
        List<Integer> permutation = new ArrayList<Integer>();
        for (int i = 0; i < map.size(); i++) {
            permutation.add(i);
        }

        for (int p = 0; p < perm; p++) {
            java.util.Collections.shuffle(permutation);
            for (int d = 0; d < file_list.size(); d++) {
                int min = matrix.length;
                for (int row = 0; row < matrix.length; row++) {
                    if (matrix[row][d] == 1) {
                        min = Math.min(min, permutation.get(row));
                    }
                }
                signature[p][d] = min;
            }
        }

        // print out the signature
        System.out.println("Signature: ");
        for (int row = 0; row < signature.length; row++) {
            for (int col = 0; col < signature[0].length; col++) {
                System.out.print(signature[row][col] + "\t");
            }
            System.out.println();
        }
        System.out.println();


        // use minHash to find the candidate pairs
        int[][] hashSig = new int[b][file_list.size()];
        int[][] candidates = new int[file_list.size()][file_list.size()];

        for (int band = 0; band < b; band++) {
            Map<String, int[]> hashColKeys = new HashMap<String, int[]>();
            for (int col = 0; col < file_list.size(); col++) {
                int r_start = band * r;
                int r_end = r_start + r - 1;

                String hashKey = hashCol(signature, col, r_start, r_end);

                if (hashColKeys.containsKey(hashKey)) {
                    int[] arr = hashColKeys.get(hashKey);
                    for (int c = 0; c < arr.length; c++) {
                        if (arr[c] == 1) {
                            candidates[c][col] = 1;
                        }
                    }
                    arr[col] = 1;
                    hashColKeys.put(hashKey, arr);
                }
                else {
                    int[] arr = new int[file_list.size()];
                    arr[col] = 1;
                    hashColKeys.put(hashKey, arr);
                }

            }

        }

        // check the candidate pairs
        // if their jaccard distance is above the threshold
        // output them as the similar pairs
        int count = 0;
        double similar = 0.8;
        System.out.println("Similar pairs are:");
        for (int row = 0; row < candidates.length; row++) {
            for (int col = row+1; col < candidates[0].length; col++) {
                if (candidates[row][col] == 1) {
                    double dis = jaccardDis(shinStorage.get(row), shinStorage.get(col));
                    if (dis > similar) {
                        System.out.println(file_list.get(row) + " " + file_list.get(col));
                        count++;
                    }
                }
            }
        }

        if (count == 0) {
            System.out.println("No similar pairs found.");
        }

    }


    private static List<String> shingles(String s, int k) {
        // this function extracts shingles out of a string
        List<String> shingles = new ArrayList<String>();

        if (s.length() == 0) {
            return shingles;
        }

        if (s.length() < k) {
            shingles.add(s);
            return shingles;
        }

        StringBuilder builder = new StringBuilder();

        int i = 0;
        int count = 0;
        while (count < k) {
            if ((s.charAt(i) == ' ' && builder.charAt(builder.length()-1) != ' ') || validate(s.charAt(i))) {
                builder.append(s.charAt(i));
                count++;
            }
            i++;
        }

        String shingle = builder.toString();
        shingles.add(shingle.toLowerCase());

        while (i < s.length()) {
            if ((s.charAt(i) == ' ' && builder.charAt(builder.length()-1) != ' ') || validate(s.charAt(i))) {
                builder.deleteCharAt(0);
                builder.append(s.charAt(i));
                shingle = builder.toString();
                shingles.add(shingle.toLowerCase());
            }
            i++;
        }

        return shingles;
    }


    private static boolean validate(char c) {
        // check for legal characters
        // as we don't consider punctuations
        if (c >= '0' && c <= '9') return true;
        else if (c >= 'A' && c <= 'Z') return true;
        else if (c >= 'a' && c <= 'z') return true;
        else return false;
    }


    private static double constrAND(int r, double p) {
        return Math.pow(p, r);
    }


    private static double constrOR(int b, double p) {
        return 1 - Math.pow(1 - p, b);
    }


    private static double roundNum(double n) {
        // round a double number as x.xxxx
        return Math.round(n * 10000) / 10000.0;
    }


    private static String hashCol(int[][] matrix, int col, int r_start, int r_end) {
        // create a hashKey
        String key = "" + matrix[r_start][col];
        for (int i = r_start+1; i <= r_end; i++) {
            key = key + "." + matrix[i][col];
        }
        return key;
    }

    private static double jaccardDis(List<String> shingle1, List<String> shingle2) {
        // calculate the Jaccard Distance between two documents based on their shingles
        Set<String> unique1 = new HashSet<String>(shingle1);
        Set<String> unique2 = new HashSet<String>(shingle2);
        Set<String> intersect = new HashSet<String>(unique1);
        intersect.retainAll(unique2);
        return intersect.size()/(double)(unique1.size() + unique2.size() - intersect.size());
    }


}










