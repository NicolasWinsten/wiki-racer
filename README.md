# wiki-racer

A bot that can play the Wikipedia Game very fast!

## Background

The [Wikipedia Game](https://en.wikipedia.org/wiki/Wikipedia:Wiki_Game) involves finding a path between two wikipedia articles through a series of clickable links.  For example, if you want to move from "Chimpanzee" to "Kevin Bacon", a possible path would be: 
<br>**[Chimpanzee -> King Kong -> Monster Movie -> _Tremors_ (film) -> Kevin Bacon]**

This game can be a competition to see who can create the optimal path with the least clicks or who can complete the path fastest.

This project was adapted from an undergraduate assignment I was given in an Intro to Software Development course last year.  The task was to create a bot that mimics a human trying to find a path as quickly as possibly.  I thought the project was fun enough to continue improving it, and this is the result.

## How To Use

You can download the jar from the packages section and just place it into your project. Alternatively, if you have a Maven project that is hooked up to Github Packages, then you can add this to your pom.xml:
```java
<dependency>
  <groupId>nicolaswinsten</groupId>
  <artifactId>wiki-racer</artifactId>
  <version>1.0</version>
</dependency>
```
After that is done, here is how you use WikiRacer:
```java
import com.nick.wikiracer.WikiRacer;
.
.
.
WikiRacer r = new WikiRacer();
List<String> path = r.findWikiLadder("Chimpanzee", "Kevin Bacon");
```
The above code will create a sequence of Wikipedia titles that connect [Chimpanzee](https://en.wikipedia.org/wiki/Chimpanzee) to [Kevin Bacon](https://en.wikipedia.org/wiki/Kevin_Bacon).
