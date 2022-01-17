package com.nicolaswinsten.wikiracer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class WikiRacerTest {

	public static void main(String[] args) {
		testFindWikiLadders(2, 500);
	}

	private static final String[] inputs = new String[] {
			"Chimpanzee:::Kevin Bacon",
			"Emu:::Stanford_University",
			"Crab:::Metaphysical_poets",
			"Brarup_Church:::The_Hunting_of_the_Snark",
			"Jimmy_Neutron:_Boy_Genius:::Ice_shanty",
			// these next input articles are simply what I got from /wiki/Special:Random
			"Latvia_at_the_1992_Summer_Olympics:::Thermae",
			"Tobiasz_Musielak:::H._Lawrence_Hinkley",
			"Cát_Bà_Island:::Fairystone_Farms_Wildlife_Management_Area",
			"Miss_Teen_USA_2006:::Ebbsfleet_Valley",
			"Ma_Zhi:::La_Vénus_d%27Ille",
			"Sandy_Ojang_Onor:::Otto_Christopher_von_Munthe_af_Morgenstierne"
	};


	public static double testFindWikiLadders(int fetchLimit, int anchorThreshold) {
		int totalTests = 0;
		int totalTime = 0;
		for (String args : inputs) {
			String[] input = args.split(":::");
			int time1 = race(input[0], input[1], fetchLimit, anchorThreshold);
			int time2 = race(input[1], input[0], fetchLimit, anchorThreshold);
			totalTime += time1 + time2;
			totalTests += 2;
		}
		return totalTime * 1.0 / totalTests;
	}

	public static void testWithDifferentLimits() {
		Map<String, Double> times = new HashMap<>();
		for(int fetchLimit = 1; fetchLimit < 5; fetchLimit++)
			for(int anchorThreshold = 500; anchorThreshold <= 3000; anchorThreshold += 500) {
				if (anchorThreshold > fetchLimit * 500) continue;
				System.out.printf("Testing with fetchLimit = %d and anchorThreshold = %d%n", fetchLimit, anchorThreshold);
				double avgtime = testFindWikiLadders(fetchLimit, anchorThreshold);
				times.put(fetchLimit + ":" + anchorThreshold, avgtime);
			}
		times.forEach(
						(k, v) -> System.out.printf("%s -> time %s%n", k, v)
		);
	}

	public void testOnSameLink() {
		race("Déjà_vu", "Déjà_vu", 1, 1);
	}
	

	private static int race(String source, String dest, int fetchLimit, int anchorThreshold) {
		System.out.println(source + " to " + dest + ":");
		long start = System.nanoTime();
		WikiRacer r = new WikiRacer(500, anchorThreshold, fetchLimit);
		List<String> path = r.findWikiLadder(source, dest);
		int time = (int) ((System.nanoTime() - start)/Math.pow(10,9));
		System.out.printf("%s\nin %d seconds%n", path, time);
		return time;
	}
	
}
