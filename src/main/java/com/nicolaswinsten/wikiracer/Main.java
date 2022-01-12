package com.nicolaswinsten.wikiracer;

import java.util.Arrays;
import java.util.List;

public class Main {
  public static void main(String[] args) {

    if (args.length < 2) {
      System.out.println("Please provide at least two valid wikipedia titles");
      System.exit(1);
    }

    System.out.printf("connecting %s...%n", Arrays.toString(args));
    WikiRacer r = new WikiRacer();

    long start = System.nanoTime();

    List<String> path = r.findWikiLadder(args[0], args[1]);
    Arrays.stream(Arrays.copyOfRange(args, 2, args.length)).forEach(
            title -> {
              String source = path.get(path.size()-1);
              List<String> segment = r.findWikiLadder(source, title);
              path.addAll(segment.subList(1, segment.size()));
            }
    );

    int time = (int) ((System.nanoTime() - start)/Math.pow(10,9));
    System.out.printf("%s\nin %d seconds%n", path, time);
  }
}
