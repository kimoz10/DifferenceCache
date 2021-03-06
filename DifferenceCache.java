import java.io.*;
import java.util.*;
/* Note assoc is the number of independent caches, not actually the n as in n-way associative */
public class DifferenceCache extends Cache{
	/* blockCount is the number of blocks in this cache */
	/* Inherited from Cache object */
	public int associativity;
	public int[] set_occupancy;
	public int max_set_count;
	public int index_bits;
	public HashMap<Integer, ArrayList<CacheEntry>> set_table;//a hashtable for sets
	
	public DifferenceCache(boolean isRemerge, MachineMemory mm, int size, int threshold, int assoc){
		remerge_count = 0;
		renegade_count = 0;
		unmerge_count = 0;
		threshold_unmerge_count = 0;
		full_set_unmerge_count = 0;
		this.occupied_block_count = 0;
		this.blockSize = 64;
		this.blockCount = size;//size of cache in blocks
		this.page_block_count_threshold = threshold;
		this.associativity = assoc;
		max_set_count = size / assoc;
		set_occupancy = new int[assoc];		
		this.isRemerge = isRemerge;
		this.mm = mm;
		this.index_bits = (int)Math.ceil(Math.log(associativity) / Math.log(2));
		cache_table = new HashMap<Page, ArrayList<CacheEntry>>();
		set_table = new HashMap<Integer, ArrayList<CacheEntry>>();

	}
	/* Given a list of cash entries, return the CacheEntry related to block number bn */
	/* input: arraylist of cache entries */
	/* returns: cache entry with block number = bbn */
	public CacheEntry blockLookup(ArrayList<CacheEntry> pagelist, int bn){
		for(CacheEntry ce: pagelist){
			if(ce.block_number == bn) return ce;
		}
		return null;
	}
	
	/* This function adds a cache entry to the oracle cache */
	/* First it checks if the cache has an entry for that specific block already */
	/* yes: update it. No change in occupancy */
	/* No: Add it to cache */
	/* When adding it to the cache, we add it to the correct set of the cache */
	/* There are two cases that can happen:
		1- The set is already full. No place. Unmerge the page with highest blocks in the set
		2- There is no more room for this page because it bypassed its threshold. Unmerge this page
	*/	
	public synchronized int addCacheEntry(CacheEntry ce) throws IOException{
		//if(ce.content==null)System.out.println("ce is null");
		Page p = ce.page;//The page to which this cache entry belongs to
		/* here we decide what the index should be */
		//System.out.println("page "+p.ppn);
		if(p.state == PageState.UNMERGED) return 0;
		int set;
		if(index_bits == 0) set = 0;
		//else set = ((p.rank % 8) + 8 * ce.block_number + 8 * 64 * (p.content_idx % ((int)(Math.pow(2, index_bits - 9)))))%associativity; /* This is what determines the index */
		else set = p.content_idx % associativity;
		ce.AssignSet(set);
		//System.out.println("set "+set);
		/* The cache has entries for page p. check to see if it has this same block number */
		if(cache_table.containsKey(p)){
			ArrayList<CacheEntry> ll = cache_table.get(p);//ll is the list of cache entries for p
			if(ll==null)System.out.println("ll is null");
			CacheEntry old = blockLookup(ll, ce.block_number);
			if(old != null){//means the cache has a block
				/* This will never cause any problems and no changes are required */
				//System.out.println("I am here");
				old.content = ce.content;//just change the content of that block
				return 0;
			}
			else {
				/* before adding the entry, we should check how many blocks this page occupies and if the set has one free block */
				int PageOccupancy = ll.size();
				int SetOccupancy = set_occupancy[set];
				//System.out.println("I am here");
				/* unmerge this page if it has more than threshold */
				if(PageOccupancy == page_block_count_threshold){
					//System.out.println("I am here");
					for(CacheEntry cache_entry: ll){
						set_occupancy[cache_entry.set]--;
						ArrayList<CacheEntry> set_entries = set_table.get(cache_entry.set);
						set_entries.remove(cache_entry);
						if(set_entries.isEmpty()) set_table.remove(cache_entry.set);
					}
					occupied_block_count -= page_block_count_threshold;
					
					p.state = PageState.UNMERGED;
					//System.out.println(p.ppn+" "+p.memory_inst);
					cache_table.remove(p);
					mm.unmerged_used_space += 4096;
					this.unmerge_count++;
					this.threshold_unmerge_count++;
				}
				else if(set_occupancy[set] == max_set_count){
					/* here we should try to find the page with maximum occupancy in the same set */
					//Page max_p = null;
					//int max_count = 0;
					//System.out.println("I am here");
					Page Evicted_Page = null;
					/* Evict a page from the set */
					/* Any page for now 	     */
					/* More optimizations later  */
					ArrayList<CacheEntry> set_list = set_table.get(set);
					for(CacheEntry cache_entry:set_list){
						if(cache_entry.page != p) {
							Evicted_Page = cache_entry.page;
							break;
						}
					}
					ArrayList<CacheEntry> ll2 = cache_table.get(Evicted_Page);
					for(CacheEntry cache_entry: ll2){
						set_occupancy[cache_entry.set] --;
						ArrayList<CacheEntry> set_entries = set_table.get(cache_entry.set);
						set_entries.remove(cache_entry);
						if(set_entries.isEmpty()) set_table.remove(cache_entry.set);
						occupied_block_count--;
					}
					cache_table.remove(Evicted_Page);
					Evicted_Page.state = PageState.UNMERGED;
					//System.out.println(Evicted_Page.ppn+" "+Evicted_Page.memory_inst);
					this.unmerge_count++;
					this.full_set_unmerge_count++;
					mm.unmerged_used_space += 4096;
					ll.add(ce);
					if(!set_table.containsKey(set)){
						ArrayList<CacheEntry> al = new ArrayList<CacheEntry>();
						al.add(ce);
						set_table.put(set, al);
					}
					else {
						set_table.get(set).add(ce);
					}
					Collections.sort(ll, new CacheEntryComparator());
					occupied_block_count++;
					set_occupancy[set]++;
								
				}
				else{
					ll.add(ce);
					if(!set_table.containsKey(set)){
						ArrayList<CacheEntry> al = new ArrayList<CacheEntry>();
						al.add(ce);
						set_table.put(set, al);
					}
					else {
						set_table.get(set).add(ce);
					}
					Collections.sort(ll, new CacheEntryComparator());
					occupied_block_count++;
					set_occupancy[set]++;
			
				
    				}
			}
			
		}
		/* The page is actually not in the cache at all */
		/* no need to check page occupancy but have to check set occupancy */
		else{
			ArrayList<CacheEntry> ll = new ArrayList<CacheEntry>();
			if(set_occupancy[set] == max_set_count){
				/* here we should try to find the page with maximum occupancy in the same set */
				Page Evicted_Page = null;
				/* Evict a page from the set */
				/* Any page for now 	     */
				/* More optimizations later  */
				ArrayList<CacheEntry> set_list = set_table.get(set);
				for(CacheEntry cache_entry:set_list){
					if(cache_entry.page != p) {
						Evicted_Page = cache_entry.page;
						break;
					}
				}
				ArrayList<CacheEntry> ll2 = cache_table.get(Evicted_Page);
				for(CacheEntry cache_entry: ll2){
					set_occupancy[cache_entry.set] --;
					ArrayList<CacheEntry> set_entries = set_table.get(cache_entry.set);
					set_entries.remove(cache_entry);
					if(set_entries.isEmpty()) set_table.remove(cache_entry.set);
					occupied_block_count--;
				}
				cache_table.remove(Evicted_Page);
				Evicted_Page.state = PageState.UNMERGED;
				//System.out.println(Evicted_Page.ppn+" "+Evicted_Page.memory_inst);
				this.unmerge_count++;
				this.full_set_unmerge_count++;
				mm.unmerged_used_space += 4096;
				ll.add(ce);
				cache_table.put(p, ll);
				if(!set_table.containsKey(set)){
					ArrayList<CacheEntry> al = new ArrayList<CacheEntry>();
					al.add(ce);
					set_table.put(set, al);
				}
				else {
					set_table.get(set).add(ce);
				}
				occupied_block_count++;
				set_occupancy[set]++;
								
			}
			else{
				ll.add(ce);
				cache_table.put(p, ll);
				if(!set_table.containsKey(set)){
					ArrayList<CacheEntry> al = new ArrayList<CacheEntry>();
					al.add(ce);
					set_table.put(set, al);
				}
				else {
					set_table.get(set).add(ce);
				}
				occupied_block_count++;
				set_occupancy[set]++;
				
			}
			
		}
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
