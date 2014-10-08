import java.io.*;
import java.util.*;
public class OracleCache extends Cache{
	
	public OracleCache(boolean isRemerge, MachineMemory mm){
		remerge_count = 0;
		renegade_count = 0;
		unmerge_count = 0;
		this.occupied_block_count = 0;
		this.blockSize = 64;
		this.blockCount = -1;
		this.page_block_count_threshold = -1;
		this.isRemerge = isRemerge;
		this.mm = mm;
		cache_table = new HashMap<Page, ArrayList<CacheEntry>>();

	}
	//Given a list of cash entries, return the CacheEntry related to block number bn
	public CacheEntry blockLookup(ArrayList<CacheEntry> ll, int bn){
		for(CacheEntry ce: ll){
			if(ce.block_number == bn) return ce;
		}
		return null;
	}
	
	/* This function adds a cache entry to the oracle cache */
	/* First it checks if the cache has an entry for that specific block already */
	/* yes: update it. No change in occupancy */
	/* No: Add it to cache */
	public synchronized int addCacheEntry(CacheEntry ce) throws IOException{
		Page p = ce.page;//The page to which this cache entry belongs to
		if(mm.isZeroPage(p.content_idx)){
			p.state=PageState.UNMERGED;
			unmerge_count++;
			mm.unmerged_used_space+=4096;
			return 0;
		}
		if(cache_table.containsKey(p)){//The cache has entries for page p. check to see if it has this same block number
			ArrayList<CacheEntry> ll = cache_table.get(p);//ll is the list of cache entries for p
			if(ll==null)System.out.println("ll is null");
			CacheEntry old = blockLookup(ll, ce.block_number);
			if(old != null){//means the cache has a block
				//System.out.println("[OracleCache Cache Addition INFO] Block "+ce.block_number+ " already in Cache");
				old.content = ce.content;//just change the content of that block
			}
			else { //should create a new cache entry here
				//System.out.println("[OracleCache Cache Addition INFO] Block "+ce.block_number+" NOT in Cache");
				ContentTableEntry cte = mm.refTable.get(p.content_idx);
				LinkedList<Page> pList = cte.pageList;//Entry of all the initially merged pages
				int total_number_difference_blocks = 0;
				int total_number_merged_pages = pList.size();
				for(Page pg: pList){
					if (pg.state == PageState.SEMI_MERGED) total_number_difference_blocks+=cache_table.get(pg).size();
				}
				/* This is the only case when we have to unmerge pages in the oracle cache. This is not valid in real difference cache */
				if(total_number_difference_blocks >= (total_number_merged_pages-1)*64){
					//System.out.println("total number of diff blocks "+total_number_difference_blocks);
					unmerge_count+=(total_number_merged_pages-1);
					for(Page pg: pList){
						//System.out.println("Page ["+pg.ppn+","+pg.memory_inst+"] unmerged");
						pg.state = PageState.UNMERGED;
						//remove all entries from cache
						int size = cache_table.get(pg).size();
						cache_table.remove(pg);
						occupied_block_count -= size;
						
					}
					mm.unmerged_used_space += 4096*(total_number_merged_pages);
                                        mm.merged_used_space -= 4096;
				}
				else{
					ll.add(ce);
					Collections.sort(ll, new CacheEntryComparator());
					//synchronized(this){
					occupied_block_count++;
    				}
				//}
			}
			//This ensures the cache entries of a page is already sorted
		}
		else{
			ArrayList<CacheEntry> ll = new ArrayList<CacheEntry>();
			ll.add(ce);
			cache_table.put(p, ll);
			//synchronized(this){
			occupied_block_count++;
			//}
		}
		//if(isRemerge) checkRenegade(ce);
		//if(isRemerge && p.state!=PageState.UNMERGED) Remerge(ce);
		//TO DO return value
		return 0;
	}

	
 	public synchronized int Remerge(CacheEntry ce) throws IOException{	
                Page refPage = ce.page;
		int mm_index = refPage.content_idx;
		LinkedList<Page> pageList = mm.refTable.get(mm_index).pageList;//pages in the refTable of the memory associated with the cache
		byte[] content = mm.refTable.get(mm_index).content;//content of the reference page
		ArrayList<CacheEntry> refList = cache_table.get(refPage);//The cache entries of the page that just inserted ints cache entry
		//Should return at least an arraylist of size 1 (refList) (The CacheEntry List)
		//The assumption is that this function takes only semi-merged pages
		int page_numbers = 0;
		for(Page p: pageList){
			if(p.state == PageState.UNMERGED) continue;
			if(p.state == PageState.MERGED){
				//System.out.println("[OracleCache REMERGE INFO] Page ("+p.ppn+","+p.memory_inst+") is still merged. No Remerge opportunity");
				return -1;
			}
			ArrayList<CacheEntry> ll = cache_table.get(p);//should return an array of cache entries for page p
			
			int comp_result = compare(ll, refList);
			if(comp_result==-1){
				return -1;
			}
			page_numbers++;
			
		}
		//System.out.println("[Enhanced Oracle INFO] Remerging opportunity found");
		//System.out.println("[Enhanced Oracle INFO] Pages To Be Remerged "+page_numbers);
		/*
		BufferedWriter out = new BufferedWriter(new FileWriter("RemergeStats.txt",true));
		out.write("PAGES\n");
		for(Page p:pageList) out.write("["+p.ppn+","+p.memory_inst+"]"+" ");
		out.write("\n");
		for (CacheEntry cacheentry: refList){
			out.write("cacheEntry block number: "+cacheentry.block_number+"\n");
			out.write("cacheEntry Content: \n");
			for(int j = 0; j < cacheentry.content.length; j++) out.write(cacheentry.content[j]+" ");
			out.write("\n");
		}
		out.write("************************************\n");
		out.close();
		*/
		this.remerge_count++;
		//System.out.println("[Enhanced Oracle INFO] REMERGE COUNT "+remerge_count);
		//remerging
		int number_entries_per_page = 0;
		for(CacheEntry cache_entry: refList){
			byte[] blockContent = cache_entry.content;
			int block_number = cache_entry.block_number;
			for(int i= 0; i<64; i++){
				content[block_number*64+i] = blockContent[i];
			}
			number_entries_per_page++;
		}
		//Updating the Oracle Cache occupancy
		occupied_block_count-=(page_numbers * number_entries_per_page);
		//removing the pages from cache
		for(Page p: pageList){
			p.state = PageState.MERGED;
			cache_table.remove(p);
		}
		return 0;
	}
	//This comparison is to decide on remerging opportunities
	private int compare(ArrayList<CacheEntry> ll, ArrayList<CacheEntry> refList){
		//Since the cache entries for a given page is already sorted by block number
		//comparing should be trivial
		//if(ll == null) return -1;
		if(ll.size() != refList.size()) return -1;
		else
		{
			for(int i=0; i<refList.size(); i++){
				if(!Arrays.equals(ll.get(i).content, refList.get(i).content))
					return -1;
			}
		}
		return 0;
	}
}
