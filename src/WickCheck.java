import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

public class WickCheck
{
	private static final String USER_AGENT = "@/RamenChef Wick Checker v0.5.0";
	
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
		
		try (Scanner in = new Scanner(System.in))
		{
			if (article.indexOf('/') == -1)
				article = "Main/" + article;
			List<Element> wicks;
			try
			{
				String base = "https://tvtropes.org/pmwiki/pmwiki.php/" + article;
				URLConnection connection = connectTo(base);
				try (InputStream is = connection.getInputStream())
				{
					wicks = Jsoup.parse(is, null, base).select("#main-article a");
				}
			}
			catch (IOException e)
			{
				System.err.println(e.getMessage() == null ? "Could not get wicks for page." : "Could not get wicks for page: " + e.getMessage());
				return;
			}
			if (count == -1)
			{
				if (wicks.size() <= 50)
					count = wicks.size();
				else if (wicks.size() > 2401) // 49^2
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
				selected.add(wicks.get(swapi).absUrl("href"));
				wicks.set(swapi, wicks.get(i));
				// don't bother doing a full swap for the indices that will never be used again
			}
		}
	}
	
	private static URLConnection connectTo(String loc) throws IOException
	{
		URLConnection connection = (new URL(loc)).openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.connect();
		return connection;
	}
}