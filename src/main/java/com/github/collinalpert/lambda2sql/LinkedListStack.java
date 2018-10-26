package com.github.collinalpert.lambda2sql;

import java.util.EmptyStackException;

/**
 * Custom implementation of a stack that uses a linked list functionality approach.
 *
 * @param <T> The type of the Stack.
 * @author Collin Alpert
 * @see Node
 */
public class LinkedListStack<T> {

	/**
	 * Element representing the head of the stack and the entry point for finding other elements.
	 */
	private Node head;
	private int elementCount;

	/**
	 * Constructor for creating an empty stack.
	 */
	public LinkedListStack() {
		elementCount = 0;
	}

	/**
	 * Pushes (adds) an element to the top of the stack.
	 *
	 * @param element The element to be added.
	 */
	public void push(T element) {
		head = new Node(head, element);
		elementCount++;
	}

	/**
	 * Pops (removes and returns) the top of the stack.
	 *
	 * @return The top element on the stack.
	 */
	public T pop() {
		if (isEmpty()) {
			throw new EmptyStackException();
		}
		Node firstElement = head;
		head = head.getNext();
		elementCount--;
		return firstElement.getValue();
	}

	/**
	 * Returns the top of the stack.
	 *
	 * @return The first element on the stack.
	 */
	public T top() {
		if (isEmpty()) {
			throw new EmptyStackException();
		}
		return head.getValue();
	}

	/**
	 * Checks if the stack is empty.
	 *
	 * @return <code>True</code> if there are elements in the stack, otherwise <code>false</code>.
	 */
	public boolean isEmpty() {
		return elementCount == 0;
	}

	/**
	 * @return the number of elements in this stack.
	 */
	public int size() {
		return elementCount;
	}

	/**
	 * A class which represents an element in a stack and includes a reference to the next element, as per linked list approach.
	 *
	 * @author Collin Alpert
	 * @see LinkedListStack
	 */
	private class Node {

		/**
		 * Reference to the next element in the stack.
		 */
		private Node next;
		private T value;

		/**
		 * Constructor for creating an element with a value and a reference to the next element in the stack.
		 *
		 * @param next  The next element in the stack.
		 * @param value This element's value.
		 */
		Node(Node next, T value) {
			this.next = next;
			this.value = value;
		}

		/**
		 * @return the reference to the next element in the stack.
		 */
		Node getNext() {
			return next;
		}

		/**
		 * @return the value of this element.
		 */
		T getValue() {
			return value;
		}
	}
}
