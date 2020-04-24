package com.fidelity.fbt.chaos.refapp.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;
import java.util.stream.Collectors;

public class FunctionalInterfacesTest {
    public static void main(String[] args) {
        List<String> arrayList = new ArrayList();
        arrayList.add("David");
        arrayList.add("Sam");
        arrayList.add("Ben");
        //Supplier- Takes no args but returns some value
        System.out.println("\n%%%%% Test Supplier %%%%%%");
        Supplier<List<String>> supplier = () -> arrayList;
        supplier.get().stream().forEach((x) -> System.out.println(x));

        //Predicate - accepts one argument and returns true/false
        System.out.println("\n%%%%% Test Predicate %%%%%%");
        Predicate<String> predicate = (name) -> name.length() >= 4;
        arrayList.stream().filter((name) -> predicate.test(name)).collect(Collectors.toList())
                .forEach((x) -> System.out.println(x));

        //Consumer - Accepts one arg but returns nothing
        System.out.println("\n%%%%% Test Consumer %%%%%%");
        Consumer<List<String>> consumer = (list) -> list.add("Tareque");
        consumer.accept(arrayList);
        arrayList.stream().forEach((x) -> System.out.println(x));

        //Function - Accepts one arg and returns one value
        System.out.println("\n%%%%% Test Function %%%%%%");
        Function<List<String>, String> function = (list) -> list.stream().collect(Collectors.joining("::"));
        System.out.println(function.apply(arrayList));


        //BiFunction - Accepts two args and returns one value
        System.out.println("\n%%%%% Test BiFunction %%%%%%");
        StringBuilder stringBuilder = new StringBuilder("Name and length of name ").append("\n");
        BiFunction<List<String>, StringBuilder, String> biFunction =
                (list, sb) -> {
                    String collect = list.stream().map(s -> String.format("%s-%d", s, s.length()))
                            .collect(Collectors.joining("::"));
                    return sb.append(collect).toString();
                };
        System.out.println(biFunction.apply(arrayList, stringBuilder));
    }

    public static Predicate<String> getStringPredicate() {
        return (name) -> name.length() >= 5;
    }


}
