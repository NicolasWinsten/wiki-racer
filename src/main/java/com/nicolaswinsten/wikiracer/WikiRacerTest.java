package com.nicolaswinsten.wikiracer;

import java.util.List;

import org.junit.Test;


/**
 * Cursory tests. Not meant to verify deploy readiness.
 * Just some test input to see if WikiRacer is working properly.
 * @author Nicolas Winsten
 *
 */
public class WikiRacerTest {

	private static String[] inputs = new String[] {
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
	
	/**
	 * Run this after all other tests
	 */
	@Test
	public void testFindWikiLadder() {
		for (String args : inputs) {
			String[] input = args.split(":::");
			race(input[0], input[1]);
			race(input[1], input[0]);
		}
	}
	
	@Test
	public void testOnSameLink() {
		List<String> path = race("Déjà_vu", "Déjà_vu");
	}
	
	/**
	 * Test WikiRacer on given input.
	 * @param source
	 * @param dest
	 */
	private static List<String> race(String source, String dest) {
		System.out.println(source + " to " + dest + ":");
		long start = System.nanoTime();
		WikiRacer r = new WikiRacer();
		List<String> path = r.findWikiLadder(source, dest);
		int time = (int) ((System.nanoTime() - start)/Math.pow(10,9));
		System.out.printf("%s\nin %d seconds%n", path, time);
		return path;
	}
	
}
