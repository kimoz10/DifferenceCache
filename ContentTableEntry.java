import java.util.*;
class ContentTableEntry{
	public boolean isZeroPage;
        public byte[] content;
        public LinkedList<Page> pageList;
        public ContentTableEntry(LinkedList<Page> pl, byte[] content, boolean isZero){
                pageList = pl;
                this.content = content;
		isZeroPage = isZero;
        }
	public boolean isZeroPage(){
		return this.isZeroPage;
	}
}

