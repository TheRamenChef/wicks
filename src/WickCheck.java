import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class WickCheck
{
	private static final String USER_AGENT = "@/RamenChef Wick Checker v0.5.0";
	
	// matches any valid WikiWord markup
	// group 2 is namespace, group 3 is non-curly bracket title, group 4 is curly bracket namespace, group 5 is curly bracket title
	private static final Pattern WIKI_WORD = Pattern.compile("(\\[=.*?=\\])|\\b(?:([A-Z][a-zA-Z0-9]*)[/.])?(?:([A-Z](?=[a-zA-Z0-9]*[A-Z])(?=[a-zA-Z0-9]*[a-z])[a-zA-Z0-9]*)\\b|\\{\\{(?:([A-Z][a-zA-Z0-9]*)[/.])?([a-zA-Z][-a-zA-Z0-9_ \t]*(?:\\b\\|[a-zA-Z][-a-zA-Z0-9_ \t]*)?)\\b\\}\\})");
	private static final Pattern NOT_ALPHANUM = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);
	
	public static void main(String[] args)
	{
		String article;
		int count;
		if (args.length == 1 || args.length == 2)
		{
			article = args[0];
			count = -1; // not determined until the "Related" page is actually fetched
		}
		else if ((args.length == 3 || args.length == 4) && "-c".equals(args[0]))
		{
			article = args[2];
			try
			{
				count = Integer.parseUnsignedInt(args[1]);
			}
			catch (NumberFormatException e)
			{
				System.err.println("Invalid wick count: " + args[1]);
				return;
			}
			if (count <= 0)
			{
				System.err.println("Invalid wick count: " + args[1]);
				return;
			}
		}
		else
		{
			System.err.println("Usage: java WickCheck [-c count] <article> [outfile]");
			return;
		}
		if (!article.matches("^(?:[a-zA-Z0-9]+/)?[a-zA-Z0-9]+$"))
		{
			System.err.println("Invalid wiki word: " + article);
			return;
		}
		
		List<String> correct, misuse, indeterminate;
		try (Scanner in = new Scanner(System.in))
		{
			if (article.indexOf('/') == -1)
				article = "Main/" + article;
			Collection<Element> redirects;
			List<Element> links;
			String base = "https://tvtropes.org/pmwiki/pmwiki.php/" + article;
			try (InputStream is = connectTo(base).getInputStream())
			{
				Document doc = Jsoup.parse(is, null, base);
				redirects = doc.select("#main-article .acaptionright a");
				links = doc.select("#main-article a");
			}
			catch (IOException e)
			{
				System.err.println(e.getMessage() == null ? "Could not get wicks for page." : "Could not get wicks for page: " + e.getMessage());
				return;
			}
			Collection<String> names = new HashSet<>(redirects.size());
			for (Element link : redirects)
			{
				String name = link.absUrl("href");
				names.add(name.substring(name.lastIndexOf('=') + 1).toLowerCase());
			}
			List<String> wicks = new ArrayList<>(links.size()); // most links shouldn't be redirects so this is a good estimate
			for (Element link : links)
			{
				String name = link.absUrl("href");
				name = name.substring(name.lastIndexOf('/', name.lastIndexOf('/') - 1) + 1);
				if (!names.contains(name.toLowerCase()))
					wicks.add(name);
			}
			if (wicks.isEmpty())
			{
				System.out.println("Article has no wicks.");
				return;
			}
			if (count == -1)
			{
				if (wicks.size() <= 50)
					count = wicks.size();
				else if (wicks.size() > 2500)
					count = (int)Math.ceil(Math.sqrt(wicks.size()));
				else
					count = 50;
			}
			else if (wicks.size() < count)
			{
				System.out.print("Actual wick count is less than supplied wick count. Continue anyway? (y/n) ");
				String answer;
				do
				{
					answer = in.nextLine();
				}
				while (!("y".equalsIgnoreCase(answer) || "n".equalsIgnoreCase(answer)));
				if ("n".equals(answer))
					return;
				count = wicks.size();
			}
			List<String> selected = new ArrayList<>(count);
			Random rand = new SecureRandom();
			for (int i = 0; i < count; i++)
			{
				int swapi = rand.nextInt(wicks.size() - i) + i;
				selected.add(wicks.get(swapi));
				wicks.set(swapi, wicks.get(i));
				// don't bother doing a full swap for the indices that will never be used again
			}
			
			correct = new ArrayList<>();
			misuse = new ArrayList<>();
			indeterminate = new ArrayList<>();
			
			ExecutorService executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "article downloader"));
			Iterator<String> iter = selected.iterator();
			String currentTitle = null;
			Future<String> currentArticle = null;
			Future<String> nextArticle;
			do
			{
				final String next = iter.next();
				nextArticle = iter.hasNext() ? executor.submit(() -> request(next, names)) : null;
				if (currentArticle != null)
				{
					int i1 = currentTitle.lastIndexOf('/') + 1;
					int i2 = currentTitle.lastIndexOf('/', i1 - 2) + 1;
					String ww = currentTitle.substring("Main/".equals(currentTitle.substring(i2, i1)) ? i1 : i2);
					String toPrint;
					try
					{
						toPrint = currentArticle.get();
					}
					catch (InterruptedException e)
					{
						break;
					}
					catch (ExecutionException e)
					{
						toPrint = "[Could not fetch article content automatically" + (e.getCause().getMessage() == null ? "" : ": " + e.getCause().getMessage()) + ']' + System.lineSeparator();
					}
					synchronized (System.out)
					{
						System.out.println();
						System.out.println("Article: " + ww);
						System.out.print(toPrint);
						System.out.println("Type m for misuse, c for correct usage, or z for indeterminate. Type x to exit.");
					}
					String answer;
					do
					{
						answer = in.nextLine();
					}
					while (!("c".equals(answer) || "m".equals(answer) || "z".equals(answer) || "x".equals(answer)));
					if ("x".equals(answer))
					{
						executor.shutdownNow();
						return;
					}
					("c".equals(answer) ? correct : "m".equals(answer) ? misuse : indeterminate).add(ww.replace('/', '.'));
				}
				currentArticle = nextArticle;
				currentTitle = next;
			}
			while (nextArticle != null);
			executor.shutdown();
			Comparator<String> articleComparator = (a, b) -> {
				int sa = a.indexOf('/');
				int sb = b.indexOf('/');
				if (sa == -1 && sb != -1)
					return -1;
				if (sb == -1)
					return 1;
				return a.compareToIgnoreCase(b);
			};
			correct.sort(articleComparator);
			misuse.sort(articleComparator);
			indeterminate.sort(articleComparator);
			// TODO: calculate a p-value
			StringBuilder builder = new StringBuilder("%%");
			builder.append(System.lineSeparator());
			builder.append("%% Wick check summary generated using " + USER_AGENT + " at ");
			builder.append(new Date());
			builder.append(System.lineSeparator());
			builder.append("%%");
			builder.append(System.lineSeparator());
			builder.append("[[foldercontrol]]");
			builder.append(System.lineSeparator());
			if (!correct.isEmpty())
				appendFolder("Correct Usage", builder, correct);
			if (!misuse.isEmpty())
				appendFolder("Misuse", builder, misuse);
			if (!misuse.isEmpty())
				appendFolder("Indeterminate", builder, indeterminate);
			builder.append("Summary: '''");
			builder.append(correct.size());
			builder.append("''' correct usage, '''");
			builder.append(misuse.size());
			builder.append("''' misuse, and '''");
			builder.append(indeterminate.size());
			builder.append("''' indeterminate.");
			
			if (args.length % 2 == 0)
			{
				try (OutputStream os = new FileOutputStream(args[args.length - 1]))
				{
					os.write(builder.toString().getBytes());
					return;
				}
				catch (IOException e)
				{
					synchronized (System.out)
					{
						System.out.println("Could not save wick check summary to file" + (e.getMessage() == null ? "." : ": " + e.getMessage()));
						System.out.print("Print to STDOUT? (y/n) ");
					}
					String answer;
					do
					{
						answer = in.nextLine();
					}
					while (!("y".equalsIgnoreCase(answer) || "n".equalsIgnoreCase(answer)));
					if ("n".equals(answer))
						return;
				}
			}
			System.out.println();
			System.out.println(builder);
		}
	}
	
	private static void appendFolder(String name, StringBuilder builder, List<String> contents)
	{
		builder.append("[[folder:");
		builder.append(name);
		builder.append("]]");
		builder.append(System.lineSeparator());
		for (String article : contents)
		{
			builder.append("* ");
			if (article.startsWith("Main/"))
				builder.append(article.substring(5));
			else
				builder.append(article);
			builder.append(System.lineSeparator());
		}
		builder.append("[[/folder]]");
		builder.append(System.lineSeparator());
	}
	
	private static URLConnection connectTo(String loc) throws IOException
	{
		URLConnection connection = (new URL(loc)).openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.connect();
		return connection;
	}
	
	private static String request(String loc, Collection<String> lookFor) throws IOException
	{
		StringBuilder builder = new StringBuilder();
		List<String> bulletStack = new ArrayList<>();
		int foundLevel = 0;
		try (Scanner scanner = new Scanner(connectTo("https://tvtropes.org/pmwiki/pmwiki.php/" + loc + "?action=source").getInputStream()))
		{
			while (!Thread.interrupted() && scanner.hasNextLine())
			{
				String line = scanner.nextLine();
				if (line.trim().isEmpty())
					continue;
				while (scanner.hasNextLine() && line.endsWith("\\\\"))
					line += System.lineSeparator() + scanner.nextLine();
				line = line.replace('\u001b', '\0'); // these might be a security risk with a terminal
				final int level = level(line);
				if (foundLevel > 0 && level > foundLevel)
				{
					if (level > foundLevel)
					{
						builder.append(line);
						builder.append(System.lineSeparator());
						continue;
					}
					else
						foundLevel = 0;
				}
				bulletStack.removeIf(s -> level(s) <= level);
				bulletStack.add(line);
				Matcher m = WIKI_WORD.matcher(line);
				while (m.find())
				{
					if (m.group(1) != null)
						continue; // escaped
					String namespace = "Main";
					String title = m.group(3);
					if (title == null) // curly bracket notation
					{
						title = NOT_ALPHANUM.matcher(m.group(5)).replaceAll("");
						if (m.group(4) != null)
							namespace = m.group(4);
					}
					if (m.group(2) != null)
						namespace = m.group(2);
					if (lookFor.contains(namespace.toLowerCase() + '/' + title.toLowerCase()))
					{
						for (String bullet : bulletStack)
						{
							builder.append(bullet);
							builder.append(System.lineSeparator());
						}
						bulletStack.clear();
						foundLevel = level;
						break;
					}
				}
			}
		}
		return builder.toString();
	}
	
	private static int level(String line)
	{
		int level;
		for (level = 0; line.charAt(level) == '*' || line.charAt(level) == '-'; level++);
		return level;
	}
}