package org.got5.tapestry5.cdi.beans;

import org.got5.tapestry5.cdi.annotation.Iced;

import javax.enterprise.context.SessionScoped;

/**
 * User: pierremarot
 * Date: 30/04/13
 * Time: 17:58
 */
@Iced
@SessionScoped
public class IceCreamImpl implements Dessert{

    private String name = "Sorbet";

    private String secondName = "Snow Cones";


    public String getName(){
        return name;
    }

    public String getOtherName(){
        return secondName;
    }

    public void setName(String name){
        this.name = name;
    }

    public void changeName(){
        name = secondName;
    }

    public boolean getCheckName(){
        return name.equals(secondName);
    }
}