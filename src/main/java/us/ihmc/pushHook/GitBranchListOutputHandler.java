package us.ihmc.pushHook;

import com.atlassian.bitbucket.io.*;
import com.atlassian.bitbucket.scm.*;
import java.util.*;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * Parse list of branches
 * <p/>
 * Example command and output:
 *
 * <pre>
 * $ git branch --contains 18f044355767fe3f5a62b9da488d20aee27a20a8
 * master
 * feature-branch1
 * feature-branch2
 * </pre>
 */
public class GitBranchListOutputHandler extends LineReaderOutputHandler implements CommandOutputHandler<List<String>>
{
   private final List<String> branches = new Vector<String>();

   public GitBranchListOutputHandler()
   {
      super(Charset.defaultCharset());
   }

   @Override
   public List<String> getOutput()
   {
      return branches;
   }

   @Override
   protected void processReader(LineReader lineReader) throws IOException
   {
      String branch;
      while ((branch = lineReader.readLine()) != null)
      {
         branch = branch.replace("* ", "").trim(); // strip the highlighting on the current branch
         branches.add(branch);
      }
   }
}