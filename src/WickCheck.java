public class WickCheck
{
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
	}
}