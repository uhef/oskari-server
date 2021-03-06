package fi.nls.oskari.domain;

public class SelectItem implements Comparable<SelectItem> {

	String name ="";
	String value ="";
	
	public int compareTo(SelectItem si2) {
		return this.getName().compareTo(si2.getName());
	}
	
	public SelectItem(String name, String value) {
		super();
		this.name = name;
		this.value = value;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	
	

}
