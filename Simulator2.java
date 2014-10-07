import java.io.*;
import java.util.*;

class PageSharingThread extends Thread{
	byte[] fileBuffer1;
	byte[] fileBuffer2;
	HashMap<Integer, Integer> table_image1;
	//HashMap<Integer, Integer> table_image2;
	MachineMemory mm;
	public PageSharingThread(MachineMemory machinememory, byte[] fb1, byte[] fb2, HashMap<Integer, Integer> tb1)
	{
		table_image1 = tb1;
		//table_image2 = tb2;
		fileBuffer1 = fb1;
		fileBuffer2 = fb2;
		mm = machinememory;
	}
	public void run(){
		ArrayList<ContentTableEntry> refTable = mm.refTable;
		for(final ContentTableEntry cte: refTable){
			final byte[] refContent = cte.content;
			LinkedList<Page> pageList = cte.pageList;
			
			for(Page p: pageList){
				// count number of merged or semi merged pages in a group
				int number_of_merged = 0;
				for(Page pq: pageList){
					if(pq.state != PageState.UNMERGED) number_of_merged++;
				}
				if(number_of_merged == 1) break;//break if there is only one page remaining
				if(p.state == PageState.UNMERGED) continue;
				boolean different = false;
				int ppn = p.ppn;
				int mem_inst = p.memory_inst;
				int newContent_idx;
				newContent_idx = ppn;
				if(newContent_idx >= 262144/2) newContent_idx -= 262144/2;
				//else
				//	newContent_idx = table_image2.get(ppn);
				byte[] fileBuffer;
				if(p.ppn < 262144/2)
					fileBuffer = fileBuffer1;
				else
					fileBuffer = fileBuffer2;
				//else fileBuffer = fileBuffer2;
				int blocknumber;
				int number_changed_blocks = 0;
                                for(blocknumber = 0; blocknumber < 64; blocknumber++){
					for(int j=0; j<64; j++){
						if(refContent[blocknumber*64+j]!=fileBuffer[newContent_idx*4096+blocknumber*64+j]){
							/* UNMERGE */
							//System.out.println("Page ("+p.ppn+", "+p.memory_inst+") unmerging");
							p.state = PageState.UNMERGED;
							if(mm.isZeroPage(p.content_idx))
								mm.zeroPage_unmerge_count++;
							else
								mm.unmerge_count++;
							mm.unmerged_used_space += 4096;
							different = true;
							break;
						}
						
					}
					if(different == true) break;
				}			
			}
		}
	}
}

class CacheThread extends Thread{

	byte[] fileBuffer1;
	byte[] fileBuffer2;
	HashMap<Integer, Integer> table_image1;
	//HashMap<Integer, Integer> table_image2;
	MachineMemory mm;
	Cache c;
	
	public CacheThread(Cache cache, MachineMemory machinememory,byte[] fb1, byte[] fb2, HashMap<Integer, Integer> tb1)
	{
		table_image1 = tb1;
		//table_image2 = tb2;
		fileBuffer1 = fb1;
		fileBuffer2 = fb2;
		c = cache;
		mm = machinememory;
	}
	public void run(){
		ArrayList<ContentTableEntry> refTable = mm.refTable;
		for(final ContentTableEntry cte: refTable){
                                final byte[] refContent = cte.content;
                                LinkedList<Page> pageList = cte.pageList;//A list of pages initially identical
                                for(Page p:pageList){
					
					int number_of_unmerged = 0;
					for(Page pq: pageList){
						if(pq.state == PageState.UNMERGED) number_of_unmerged++;
					}
					if(number_of_unmerged == (pageList.size() - 1)) break;
					/* if the page is unmerged, it is not part of the page group anymore, we dont care about it */
                                        if(p.state == PageState.UNMERGED) continue;
                                        int ppn = p.ppn;//get physical page number of the page
                                        //TO COMPLETE
                                        int mem_inst = p.memory_inst;//memory instance of the page
                                        int newContent_idx;//get index in filebuffer to read the new content of the page
                                        newContent_idx = ppn;
					if(newContent_idx >= 262144/2) newContent_idx -= 262144/2;
                                        //else
                                        //        newContent_idx = table_image2.get(ppn);
                                        byte[] fileBuffer;
					if(p.ppn < 262144/2) fileBuffer = fileBuffer1;
					else fileBuffer = fileBuffer2;
                                        
                                        //else{
                                        //        fileBuffer = fileBuffer2;
                                        //}
                                        
                                        int blocknumber;
                                        int number_changed_blocks = 0;
					
                                        for(blocknumber = 0; blocknumber < 64; blocknumber++){
						
						if(p.state==PageState.UNMERGED){
							//System.out.println("Page already unmerged");
							break;//Because UNMERGE can happen when a.addCacheEntry is called
						}
 						boolean entry_added = false;                                               
                                                for(int j=0; j<64; j++){
                                                        if(refContent[blocknumber*64+j]!=fileBuffer[newContent_idx*4096+blocknumber*64+j]){
                                                                //ArrayList<Byte> Block = (ArrayList<Byte>)fileBuffer.subList(newContent_idx*4096+blocknumber*64, newContent_idx*4096+(blocknumber+1)*64);
								//Byte[] new_block_b4 = Block.toArray(new Byte[Block.size()]);
								
								byte[] new_block = Arrays.copyOfRange(fileBuffer, newContent_idx*4096+blocknumber*64, newContent_idx*4096+(blocknumber+1)*64);
								p.state = PageState.SEMI_MERGED;//May be should only check this from caches as we have different cache configs
                                                                CacheEntry ce = new CacheEntry(p, blocknumber, new_block);
								try{
									
                                                                	c.addCacheEntry(ce);//This can result in an UNMERGE
									
								}
								catch(Exception e){
									e.printStackTrace();
								}
								entry_added = true;
                                                                break;
                                                        }
                                                }
						//Reaching this part means the block under test is identical to the content block
						//We should check if relapsing occured
						//System.out.println("here");
						/*
						int test = 1000;
						if(entry_added == false){
							if(c.cache_table.containsKey(p) && c.isRemerge){
								ArrayList<CacheEntry> list  = c.cache_table.get(p);
								for(CacheEntry entry: list){
									if (entry.block_number == blocknumber){//Renegade case found
										/*
										if(test >= 0){
											System.out.println("[RELAPSE] Entry in the Cache: ");
											for(int b = 0; b < 64; b++){
												System.out.println(refContent[blocknumber*64+b]+" "+entry.content[b]+" "+fileBuffer[newContent_idx*4096+blocknumber*64+b]);
											}
											//System.out.println();
											test--;
										}//end comment here
										c.renegade_count++;
										list.remove(entry);
										c.occupied_block_count--;
										if(list.size()==0){
											p.state=PageState.MERGED;
											c.cache_table.remove(p);
										}
										break;
									}
								}
							}
						}*/
                                        }
					
                                }
                }
		

	}
}

public class Simulator2{

	public static byte[] getPage(int index, int img_no, String fname) throws IOException{
    		File img = new File("/home/karim/qemu/image3/data/"+fname);
    		FileInputStream fis = new FileInputStream(img);
    		byte[] buffer = new byte[4096];
		fis.skip(index*4096);
		fis.read(buffer);
		fis.close();
		return buffer;
  	}
	//ToDo
	private static void removeSample(int img_no, String fname) throws IOException{
		//if(img_no < 10) return;
		//for(int i = 0;i<10;i++)if(fname.equals("sample"+i+".txt"))return;
                File img = new File("/home/karim/qemu/image"+img_no+"/samples/"+fname);
		img.delete();
                
        }

	public static void main(String[] args) throws IOException{
		/* INITIALIZATION */
		/* Construct the MachineMemory Object from output.txt and the memory dumps */
		File f = new File("/home/karim/qemu/image3/data/output.txt");
		BufferedReader in = new BufferedReader(new FileReader(f));
		String s;
		int page_index = 0;
		int merged_pages = 0;//number of unique machine memory pages that has more than one page
				    // referencing it
		int total_number_of_pages = 0;//total number of pages that were initially merged
		MachineMemory mm = new MachineMemory(1073741824L);//this one should be for page sharing
		//MachineMemory mm_differencecache1way = new MachineMemory(1*1024*1024*1024);
		//MachineMemory mm_differencecache2way = new MachineMemory(1*1024*1024*1024);
		//MachineMemory mm_differencecache4way = new MachineMemory(1*1024*1024*1024);
		//MachineMemory mm_differencecache8way = new MachineMemory(1*1024*1024*1024);
		//MachineMemory mm_differencecacheFA = new MachineMemory(1*1024*1024*1024);
                MachineMemory mm_oraclecache_remerge = new MachineMemory(1073741824L);
		int zeroPageCount = 0;
		while((s = in.readLine()) != null){
			LinkedList<Page> pageList1 = new LinkedList<Page>();
			//LinkedList<Page> pageList2 = new LinkedList<Page>();
			//LinkedList<Page> pageList3 = new LinkedList<Page>();
			//LinkedList<Page> pageList4 = new LinkedList<Page>();
			LinkedList<Page> pageList5 = new LinkedList<Page>();
			//LinkedList<Page> pageList6 = new LinkedList<Page>();
			//LinkedList<Page> pageList7 = new LinkedList<Page>();
			String[] item = s.split(" ");
			boolean isZero = false;
			
			for(int i = 0; i < item.length; i++){
				if(item[i].equals("ZERO:")){
					isZero = true;
					zeroPageCount = item.length - 1;//this is the number of zero pages
					mm.setZeroPageCount(zeroPageCount);
					mm_oraclecache_remerge.setZeroPageCount(zeroPageCount);
					continue;
					//break;//This totally ignores the zero pages
				}
				String[] page_property = (item[i].substring(1, item[i].length()-1)).split(",");
				int ppn = Integer.parseInt(page_property[0]);
				int mem_inst;
				if(page_property.length == 1)
					mem_inst = 2;//2 means page started out as similar
				else
					mem_inst = 1;//1 means page started out as identical
				Page pg1 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
				pageList1.add(pg1);
				//Page pg2 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
                                //pageList2.add(pg2);
				//Page pg3 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
                                //pageList3.add(pg3);
				//Page pg4 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
                                //pageList4.add(pg4);
				Page pg5 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
                                pageList5.add(pg5);
				//Page pg6 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
                                //pageList6.add(pg6);
				//Page pg7 = new Page(ppn, mem_inst, page_index, PageState.MERGED, i);
                                //pageList7.add(pg7);
				if(!isZero) total_number_of_pages++;//This is the memory usage without pagesharing

			}
		        mm.addpageList(pageList1, isZero);//This will add the page list along with the common
						//reference page content that they share
			//mm_differencecache1way.addpageList(pageList2, isZero);
			//mm_differencecache2way.addpageList(pageList3, isZero);
			//mm_differencecache4way.addpageList(pageList4, isZero);
			//mm_differencecache8way.addpageList(pageList6, isZero);
			//mm_differencecacheFA.addpageList(pageList7, isZero);
			mm_oraclecache_remerge.addpageList(pageList5, isZero);
			page_index++;
			if(!isZero)merged_pages++;
		}
		System.out.println("total pages "+total_number_of_pages);
		in.close();
		mm.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes (only non zero pages)
		mm.unmerged_used_space = (mm.total_size/4096 - zeroPageCount - total_number_of_pages)*4096;//Space occupied by all the pages that are not considered for sharing
		System.out.println("unmerge space is "+mm.unmerged_used_space);
		/* Doing the same thing for oracle caches */
		//mm_differencecache1way.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes
		//mm_differencecache1way.unmerged_used_space = (mm.total_size/4096 - total_number_of_pages)*4096;
		//mm_differencecache2way.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes
		//mm_differencecache2way.unmerged_used_space = (mm.total_size/4096 - total_number_of_pages)*4096;
		//mm_differencecache4way.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes
		//mm_differencecache4way.unmerged_used_space = (mm.total_size/4096 - total_number_of_pages)*4096;
		//mm_differencecache8way.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes
		//mm_differencecache8way.unmerged_used_space = (mm.total_size/4096 - total_number_of_pages)*4096;
		//mm_differencecacheFA.merged_used_space = merged_pages*4096;//space occupied by all page groups in bytes
		//mm_differencecacheFA.unmerged_used_space = (mm.total_size/4096 - total_number_of_pages)*4096;
		mm_oraclecache_remerge.merged_used_space = merged_pages*4096;
                mm_oraclecache_remerge.unmerged_used_space = (mm.total_size/4096 - zeroPageCount - total_number_of_pages)*4096;
		//Now the tracing part
		//first creating a mapping between (pagenumber, memory instance) to an index inside sample.txt
		//now since we are dumping the whole memory, the index is the page number
		//File f1 = new File("/home/karim/qemu/image3/page_monitor.txt");
    		//File f2 = new File("/home/karim/qemu/image2/page_monitor.txt");
		//BufferedReader in_image1 = new BufferedReader(new FileReader(f1));
	        //BufferedReader in_image2 = new BufferedReader(new FileReader(f2));
		/* table_image is used to index a given ppn in a certain memory */
		/* the ppn is the key. the index of this ppn is the value */
		/* this is important so we can check the new value of this page */
	        final HashMap<Integer, Integer> table_image1 = new HashMap<Integer, Integer>();
		//final HashMap<Integer, Integer> table_image2 = new HashMap<Integer, Integer>();
    		//String s1;
    		//String s2;
    		//int index1 = 0; 
    		//int index2 = 0;
    		//while((s1 = in_image1.readLine())!=null){
      		//	table_image1.put(Integer.parseInt(s1), Integer.parseInt(s1));
      		//	index1++;
    		//}
		/*
    		while((s2 = in_image2.readLine())!=null){
      			table_image2.put(Integer.parseInt(s2), index2);
      			index2++;
    		}*/
    		//in_image1.close();
    		//in_image2.close();
		//end of indexing table to get index from physical page number
		//read sample i only if sample i+1 exist
		//ArrayList<Page> page_sharing_pageList  = new ArrayList<Page>();
		//final DifferenceCache differencecache_1way = new DifferenceCache(false, mm_differencecache1way, 16 * 1024, 8, 16 * 1024);//Instance of a no remerge oracle cache
		//final DifferenceCache differencecache_2way = new DifferenceCache(false, mm_differencecache2way, 16 * 1024, 8, 8 * 1024);//16*1024 = 1 MB of cache
		//final DifferenceCache differencecache_4way = new DifferenceCache(false, mm_differencecache4way, 16 * 1024, 8, 4 * 1024);
		//final DifferenceCache differencecache_8way = new DifferenceCache(false, mm_differencecache8way, 16 * 1024, 8, 2 * 1024);
		//final DifferenceCache differencecache_FA = new DifferenceCache(false, mm_differencecacheFA, 16 * 1024, 8, 1);
		final OracleCache oraclecache_remerge = new OracleCache(false, mm_oraclecache_remerge);//instance of a remerge oracle cache
		//ToDo
		//System.out.println("here");
		//final Integer i;
		//CacheThread noRemerge_t = new CacheThread(oraclecache_noremerge, mm_oraclecache_noremerge);
                //CacheThread Remerge_t = new CacheThread(oraclecache_remerge, mm_oraclecache_remerge);
		for(int i = 0; i < 300; i++){//200000 is the number of samples/instructions to read
			//CacheThread noRemerge_t = new CacheThread(oraclecache_noremerge, mm_oraclecache_noremerge);
			//CacheThread Remerge_t = new CacheThread(oraclecache_remerge, mm_oraclecache_remerge);
			//noRemerge_t.start();
			//Remerge_t.start();
			//First: Handling Oracle Caches

			
			//File WaitforFile = new File("samples/sample"+(i+1)+".txt");
			//File WaitforFile2 = new File("../image2/samples/sample"+(i+1)+".txt");
			Runtime rt = Runtime.getRuntime();
                        System.out.println(String.format("Free: %d bytes, Total: %d bytes, Max: %d bytes",rt.freeMemory(), rt.totalMemory(), rt.maxMemory()));
			/*
			while(!(WaitforFile.exists() && WaitforFile2.exists())){
				try {
    					Thread.sleep(1);
				} catch(InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
			}
			*/
			File currentFile1 = new File("/home/karim/qemu/image3/data/sample"+(i)+".txt");
			//File currentFile2 = new File("../image2/samples/sample"+i+".txt");
			FileInputStream fis1 = new FileInputStream(currentFile1);
			//FileInputStream fis2 = new FileInputStream(currentFile2);
			/* Store all the data in sample files in memory */
			/* filebuffer1 stores data from m1, filebuffer2 from m2 */
			//ArrayList<Byte> fileBuffer1 = new ArrayList<Byte>();
			byte[] fileBuffer1 = new byte[1073741824/2];
			byte[] fileBuffer2 = new byte[1073741824/2];
			fis1.read(fileBuffer1);
			fis1.read(fileBuffer2);
			/*
			int b;
			while((b = fis1.read()) != -1)
				fileBuffer1.add((byte)b);
			*/
			/*
			boolean isSampleZero = true;
			for(int j = 0; j < 512*1024*1024; j++){
				if(fileBuffer1[j]!=0){
					isSampleZero = false;
					break;
				}
				
			}
			if(isSampleZero == true) System.out.println("Zero Sample at "+i);
	//		*/
			//byte[] fileBuffer2 = new byte[1024*1024*1024];
                        //fis2.read(fileBuffer2);
			//CacheThread differencecache1way_t = new CacheThread(differencecache_1way, mm_differencecache1way, fileBuffer1, fileBuffer2, table_image1, table_image2);
			//CacheThread differencecache2way_t = new CacheThread(differencecache_2way, mm_differencecache2way, fileBuffer1, fileBuffer2, table_image1, table_image2);
			//CacheThread differencecache4way_t = new CacheThread(differencecache_4way, mm_differencecache4way, fileBuffer1, fileBuffer2, table_image1, table_image2);
			//CacheThread differencecache8way_t = new CacheThread(differencecache_8way, mm_differencecache8way, fileBuffer1, fileBuffer2, table_image1, table_image2);
			//CacheThread differencecacheFA_t = new CacheThread(differencecache_FA, mm_differencecacheFA, fileBuffer1, fileBuffer2, table_image1, table_image2);
			CacheThread Remerge_t = new CacheThread(oraclecache_remerge, mm_oraclecache_remerge,fileBuffer1,fileBuffer2,table_image1);
			PageSharingThread ps_t = new PageSharingThread(mm, fileBuffer1, fileBuffer2, table_image1);
			
			ps_t.start();			
			//differencecache1way_t.start();
			//differencecache2way_t.start();
			//differencecache4way_t.start();
			//differencecache8way_t.start();
			//differencecacheFA_t.start();
			Remerge_t.start();
			
			/* here we should handle the page sharing case */

			try{
				ps_t.join();
				//differencecache1way_t.join();
				//differencecache2way_t.join();
				//differencecache4way_t.join();
				//differencecache8way_t.join();
				//differencecacheFA_t.join();
				Remerge_t.join();
			}
			catch(Exception e){
				System.out.println("error");
			
			}
			
			fis1.close();
			//fis2.close();
			
			displayRefTable(mm, i);
			//CacheshowStats(differencecache_1way, i, "DifferenceCache1way");
			//CacheshowStats(differencecache_2way, i, "DifferenceCache2way");
			
			//CacheshowStats(differencecache_4way, i, "DifferenceCache4way");
			//CacheshowStats(differencecache_8way, i, "DifferenceCache8way");
			//CacheshowStats(differencecache_FA, i, "DifferenceCacheFA");
			CacheshowStats(oraclecache_remerge, i, "EnhancedOracle");
			//String file_to_remove = "sample"+i+".txt";
			//removeSample(1, file_to_remove);
			//removeSample(2, file_to_remove);
			
			
		}
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
		
		out.write("Memory Unmerged Occupied Space (non zero)"+mm.unmerged_used_space+"\n");
                out.write("Memory Merged Occupied Space "+mm.merged_used_space+"\n");
                //out.write("Memory Empty Space "+(mm.total_size - (mm.unmerged_used_space + mm.merged_used_space))+"\n");
		out.write("Unmerge count "+mm.unmerge_count+"\n");
		out.write("zeropage unmerge count "+mm.zeroPage_unmerge_count+"\n");
		out.write("Memory Occupied Space without pagesharing "+(mm.total_size/4096 - mm.zeroPageCount+mm.zeroPage_unmerge_count)*4096+"\n");
		out.write("Memory Occupied Space After pageSharing "+(mm.unmerged_used_space+mm.merged_used_space)+"\n");
		out.close();

	}
	//This function shows the content of a particular index of a reference table
	public static void showContent(MachineMemory mm, int idx) throws IOException{
		FileOutputStream fos = new FileOutputStream("refTableContent.txt");
		byte[] content = mm.refTable.get(idx).content;
		fos.write(content);
		fos.close();
	}

	
	public static void CacheshowStats(Cache c, int sample_number, String cacheName) throws IOException{
		String fname = cacheName+".sample"+sample_number;
		File f = new File(""+fname);
		BufferedWriter out = new BufferedWriter(new FileWriter(f));
		HashMap<Page, ArrayList<CacheEntry>> cachetable = c.cache_table;
		
		out.write("******** CACHE TABLE ********\n");
		int[] x=new int[5];
		int[] y=new int[5];
		for(int i = 0; i < 5; i++) x[i] = 0;
		for(Page p : cachetable.keySet()){
			out.write("["+p.ppn+",m"+p.memory_inst+"] => ");
			ArrayList<CacheEntry> ceList = cachetable.get(p);
			int size = ceList.size();
			if(p.memory_inst==1){
				if(size>0 && size<=4)x[0]++;
				else if(size>4 && size<=8)x[1]++;
				else if(size>8 && size<=16)x[2]++;
				else if(size>16 &&size<=32)x[3]++;
				else x[4]++;
			}
			else{
				if(size>0 && size<=4)y[0]++;
                                else if(size>4 && size<=8)y[1]++;
                                else if(size>8 && size<=16)y[2]++;
                                else if(size>16 &&size<=32)y[3]++;
                                else y[4]++;
			}
			for(CacheEntry ce: ceList)
			{
				out.write("("+ce.block_number+","+ce.set+")"+", ");
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
		out.write("Occupied Space "+(mm.merged_used_space + mm.unmerged_used_space + c.occupied_block_count*64 + c.unmerge_count*4096)+"\n");
		for(int i = 0; i<5; i++) out.write(x[i]+" ");
		out.write("\n");
		for(int i = 0; i<5; i++) out.write(y[i]+" ");
		//displayRefTable(mm, out);
                out.close();
        }

}
