package io.spm.parknshop.query.vo;

import java.util.Date;
import java.util.List;

public class AppIncomeVO {

  private Date start;
  private Date end;

  private List<AppIncomeItem> incomeList;

  private double totalProfit;
  private double adProfit;
  private double shopOrderProfit;

  public Date getStart() {
    return start;
  }

  public AppIncomeVO setStart(Date start) {
    this.start = start;
    return this;
  }

  public Date getEnd() {
    return end;
  }

  public AppIncomeVO setEnd(Date end) {
    this.end = end;
    return this;
  }

  public List<AppIncomeItem> getIncomeList() {
    return incomeList;
  }

  public AppIncomeVO setIncomeList(List<AppIncomeItem> incomeList) {
    this.incomeList = incomeList;
    return this;
  }

  public double getTotalProfit() {
    return totalProfit;
  }

  public AppIncomeVO setTotalProfit(double totalProfit) {
    this.totalProfit = totalProfit;
    return this;
  }

  public double getAdProfit() {
    return adProfit;
  }

  public AppIncomeVO setAdProfit(double adProfit) {
    this.adProfit = adProfit;
    return this;
  }

  public double getShopOrderProfit() {
    return shopOrderProfit;
  }

  public AppIncomeVO setShopOrderProfit(double shopOrderProfit) {
    this.shopOrderProfit = shopOrderProfit;
    return this;
  }
}
