package com.engine.core;

import com.engine.model.Order;
import java.util.ArrayList;
import java.util.List;

public class RingBuffer {
    private Order[] buffer;
    private int head, tail, size, capacity;

    public RingBuffer(int cap) {
        this.capacity = cap;
        this.buffer = new Order[cap];
    }

    public void enqueue(Order o) {
        if (size == capacity) {
            resize();
        }
        buffer[tail] = o;
        tail = (tail + 1) % capacity;
        size++;
    }

    public Order dequeue() {
        if (isEmpty()) return null;
        Order o = buffer[head];
        buffer[head] = null;
        head = (head + 1) % capacity;
        size--;
        return o;
    }

    public Order peek() {
        return isEmpty() ? null : buffer[head];
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int getSize() {
        return size;
    }

    public List<Order> toList() {
        List<Order> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(buffer[(head + i) % capacity]);
        }
        return list;
    }

    public boolean remove(String orderId) {
        List<Order> remaining = new ArrayList<>();
        boolean found = false;
        for (int i = 0; i < size; i++) {
            Order o = buffer[(head + i) % capacity];
            if (o.id.equals(orderId)) {
                found = true;
            } else {
                remaining.add(o);
            }
        }
        if (found) {
            head = 0;
            tail = 0;
            size = 0;
            buffer = new Order[capacity];
            for (Order o : remaining) {
                enqueue(o);
            }
        }
        return found;
    }

    private void resize() {
        Order[] nb = new Order[capacity * 2];
        for (int i = 0; i < size; i++) {
            nb[i] = buffer[(head + i) % capacity];
        }
        buffer = nb;
        head = 0;
        tail = size;
        capacity *= 2;
    }
}
