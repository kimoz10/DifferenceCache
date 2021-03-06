import java.util.*;
import java.io.*;
public class SimulateDiffCache{

	public static byte[] getPage(int index, int img_no, String fname) throws IOException{
    		File img = new File("/home/karim/qemu/image"+img_no+"/samples/"+fname);
    		FileInputStream fis = new FileInputStream(img);
    		byte[] buffer = new byte[4096];
		fis.skip(index*4096);
		fis.read(buffer);
		fis.close();
		return buffer;
  	}
		
	public static void main(String[] args) throws IOException{
		/* INITIALIZATION */
		/* Construct the MachineMemory Object from output.txt and the memory dumps */
		
		// The associativity of the difference cache
		int assoc = Integer.parseInt(args[0]);
		// The block threshold
		int threshold = Integer.parseInt(args[1]);
		File f = new File("/home/karim/qemu/image3/data/output.txt");
		BufferedReader in = new BufferedReader(new FileReader(f));
		String s;
		int page_index = 0;
		int merged_pages = 0;//number of unique machine memory pages that has more than one page
				    // referencing it
		int total_number_of_pages = 0;//total number of pages that were initially merged
		MachineMemory mm = new MachineMemory(2*1024*1024*1024);//this one should be for page sharing
		int zeroPageCount = 0;
		while((s = in.readLine()) != null){
			LinkedList<Page> pageList1 = new LinkedList<Page>();
			String[] item = s.split(" ");
			boolean isZero = false;
			// This completely ignores zero pages			
			for(int i = 0; i < item.length; i++){
				if(item[i].equals("ZERO:")){
					isZero = true;
					zeroPageCount = item.length - 1;
					continue;
				}
				String[] page_property = (item[i].substring(1, item[i].length()-1)).split(",");
				int ppn = Integer.parseInt(page_property[0]);
				int mem_inst;
				if(page_property[1].equals("m1"))mem_inst = 1;
				else mem_inst = 2;
				Page pg1 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
				pageList1.add(pg1);
				total_number_of_pages++;

			}
		        mm.addpageList2(pageList1, isZero);
			page_index++;
			merged_pages++;	
		}
		in.close();
		mm.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes
		mm.unmerged_used_space = (mm.total_size/4096 - zeroPageCount - total_number_of_pages)*4096;//Space occupied by all the pages that are not considered for sharing
		System.out.println("merged used space "+mm.merged_used_space);
		System.out.println("unmerged used space "+mm.unmerged_used_space);
		System.out.println("memory occupied is "+(mm.merged_used_space + mm.unmerged_used_space));
		
		final DifferenceCache difference_cache = new DifferenceCache(true, mm, 4*1024, threshold, assoc);//Direct mapped cache
		
		/* Now comes the part where we parse the output of an Enhanced Oracle Cache */
		for(int i = 0; i < 100000; i++){
			if(i % 1000 == 0) System.out.println(i);
			String fname = "EnhancedOracle.sample"+i;
			File file = new File("sanity/OracleCache/"+fname);
			in = new BufferedReader(new FileReader(file));
			in.readLine();
			String line;
			while( !(line = in.readLine()).equals("*************************")){
				String[] item = line.split(" => ");
				int ppn = Integer.parseInt(item[0].split(",")[0].split("\\[")[1]);
				//System.out.println(ppn);
				int mem_inst = Integer.parseInt(item[0].split(",")[1].split("\\]")[0].split("m")[1]);
				Page p = getPage(mm, ppn, mem_inst);
				LinkedList<Page> pg = mm.refTable.get(p.content_idx).pageList; 
				//if(p.ppn == 129789) System.out.println(p.state+ " at sample "+i);
				if(p.state == PageState.UNMERGED) continue;
				int unmerged_count = 0;
				for(Page pp:pg){
					if (pp.state==PageState.UNMERGED) unmerged_count++;
				}
				if(unmerged_count == pg.size() - 1) continue;
				ArrayList<Integer> BlockList = new ArrayList<Integer>();
				String[] BigBlock = item[1].split(",");
				int j = 0;
				for(String block: BigBlock){
					if(j % 2 != 0 || j==BigBlock.length - 1) {
						j++;
						continue;
					}
					j++;
					String trimmed_block = block.trim();
					//System.out.println(trimmed_block);
					int bn = Integer.parseInt(trimmed_block.split(",")[0].split("\\(")[1]);
					//System.out.println(bn);
					BlockList.add(bn);
				}
				for(Integer block: BlockList){
					if(p.state!=PageState.UNMERGED){
						CacheEntry ce = new CacheEntry(p, block, null);
						difference_cache.addCacheEntry(ce);
					}
					else{
						break;
					}
				}
			}
			in.close();
			int nice_name = 16*1024/assoc;
			String name = "DifferenceCache"+nice_name+"way"+threshold+"threshold";
			CacheshowStats(difference_cache, i, name);
		}
	}

	public static Page getPage(MachineMemory mm, int ppn, int mem_inst){
		ArrayList<ContentTableEntry> PageGroups = mm.refTable;
		for(ContentTableEntry cte: PageGroups){
			LinkedList<Page> pl = cte.pageList;
			for(Page page: pl){
				if(page.ppn == ppn && page.memory_inst == mem_inst) return page;
			}
		}				
		return null;
	}
	
	//Functions for testing purposes
	//This function displays rZZeference table in the machine memory
	public static void displayRefTable(MachineMemory mm, int sample_number) throws IOException{
		String fname = "PageSharing.sample"+sample_number;
		File f = new File("" + fname);
		BufferedWriter out = new BufferedWriter(new FileWriter(f));
				
		ArrayList<ContentTableEntry> table = mm.refTable;
                for(ContentTableEntry cte: table){
                        out.write(cte.isZeroPage+": ");
                        LinkedList<Page> l = cte.pageList;
                        for(Page p: l){
                                out.write("["+p.ppn+",m"+p.memory_inst+","+p.content_idx+","+p.state+"]");
                        }
                        out.write("\n");
                }
		
		out.write("Memory Unmerged Occupied Space "+mm.unmerged_used_space+"\n");
                out.write("Memory Merged Occupied Space "+mm.merged_used_space+"\n");
                out.write("Memory Empty Space "+(mm.total_size - (mm.unmerged_used_space + mm.merged_used_space))+"\n");
		out.write("Unmerge count "+mm.unmerge_count);
		out.close();

	}
	//This function shows the content of a particular index of a reference table
	public static void showContent(MachineMemory mm, int idx) throws IOException{
		FileOutputStream fos = new FileOutputStream("refTableContent.txt");
		byte[] content = mm.refTable.get(idx).content;
		fos.write(content);
		fos.close();
	}

	
	public static void CacheshowStats(DifferenceCache c, int sample_number, String cacheName) throws IOException{
		String fname = cacheName+".sample"+sample_number;
		File f = new File(""+fname);
		BufferedWriter out = new BufferedWriter(new FileWriter(f));
		HashMap<Page, ArrayList<CacheEntry>> cachetable = c.cache_table;
		HashMap<Integer, ArrayList<CacheEntry>> settable = c.set_table;
		out.write("******** CACHE TABLE ********\n");
		for(Page p : cachetable.keySet()){
			out.write("["+p.ppn+",m"+p.memory_inst+"] => ");
			ArrayList<CacheEntry> ceList = cachetable.get(p);
			for(CacheEntry ce: ceList)
			{
				out.write("("+ce.block_number+","+ce.set+")"+", ");
			}
			out.write("\n");
			//out.write("***********************\n");
		}
		out.write("*************************\n");
		out.write("******** SET TABLE ********\n");
		for(Integer set : settable.keySet()){
			out.write("set "+set+": ");
			ArrayList<CacheEntry> ceList = settable.get(set);
			for(CacheEntry ce: ceList)
			{
				out.write("["+ce.page.ppn+","+ce.block_number+"], ");
			}
			out.write("\n");
			//out.write("***********************\n");
		}
		out.write("*************************\n");
                out.write("Cache Occupied Blocks "+c.occupied_block_count+"\n");
		//if(c.blockCount != -1)	out.write("Cache Empty Blocks "+(c.blockCount - c.occupied_block_count)+"\n");
		//out.write("RENEGADE COUNT: "+c.renegade_count+"\n");
		//out.write("REMERGE COUNT: "+c.remerge_count+"\n");
		//out.write("******* MEMORY *******\n");
		MachineMemory mm = c.mm;
		out.write("Memory Empty Space: "+ (mm.total_size - (mm.merged_used_space + mm.unmerged_used_space))+"\n");
		out.write("Unmerge count: "+c.unmerge_count+"\n");
		out.write("Full Set Unmerge: "+c.full_set_unmerge_count+"\n");
		out.write("Threshold set unmerge: "+c.threshold_unmerge_count+"\n");
		//displayRefTable(mm, out);
                out.close();
        }

}
